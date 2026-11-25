package sn.arlettedebo.platform.finance.internal;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import sn.arlettedebo.platform.finance.internal.domain.Depense;
import sn.arlettedebo.platform.finance.internal.domain.DepenseRepository;
import sn.arlettedebo.platform.finance.internal.domain.Justificatif;
import sn.arlettedebo.platform.finance.internal.domain.JustificatifRepository;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage.FichierStocke;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Parametres;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Achats & dépenses — circuit et habilitations :
 * <ul>
 *   <li>Préparation / justificatifs : Administration ({@code administration:S}) ;</li>
 *   <li>Validation : DG ({@code administration:V}) ; la DO valide par délégation jusqu'au seuil DG ;</li>
 *   <li>Paiement : Administration ({@code administration:G}), jamais par la personne qui a validé ;</li>
 *   <li>Contrôle : DG / DO, justificatif obligatoire, personne indépendante.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepenseService {

    private final DepenseRepository depenses;
    private final JustificatifRepository justificatifs;
    private final JustificatifStorage stockage;
    private final AccessPolicy access;
    private final Referentiels referentiels;
    private final Parametres parametres;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<Depense> lister(Depense.Statut statut, Pageable page) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        return depenses.rechercher(statut, page);
    }

    @Transactional(readOnly = true)
    public Depense lire(UUID id) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        return charger(id);
    }

    public Depense demander(String objet, String categorie, String perimetre, long montant, String fournisseur) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.S);
        referentiels.exiger(TypeReferentiel.CATEGORIE_DEPENSE, categorie);
        if (perimetre != null) {
            referentiels.exiger(TypeReferentiel.PERIMETRE, perimetre);
        }
        String reference = "DEP-" + Year.now(clock) + "-" + String.format("%06d", depenses.prochainNumero());
        Depense d = depenses.save(new Depense(new Depense.Donnees(reference, objet.trim(), categorie, perimetre, montant, fournisseur.trim(),
                access.utilisateur().id(), clock.instant())));
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Demande de dépense", libelle(d)));
        return d;
    }

    public Depense valider(UUID id) {
        Depense d = charger(id);
        exigerPouvoirDeValidation(d);
        d.valider(access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Dépense validée", libelle(d)));
        return d;
    }

    public Depense refuser(UUID id, String motif) {
        Depense d = charger(id);
        exigerPouvoirDeValidation(d);
        d.refuser(access.utilisateur().id(), clock.instant(), motif);
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Dépense refusée", libelle(d) + " — " + motif));
        return d;
    }

    public Depense payer(UUID id, String modePaiement) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.G);
        referentiels.exiger(TypeReferentiel.MODE_PAIEMENT, modePaiement);
        Depense d = charger(id);
        d.payer(access.utilisateur().id(), clock.instant(), modePaiement);
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Dépense payée", libelle(d) + " · " + modePaiement));
        return d;
    }

    public Depense controler(UUID id, String commentaire) {
        if (!access.estProfil(Profils.DG, Profils.MANAGER, Profils.DO)) {
            throw DomainException.regle("CONTROLE_DIRECTION", "Le contrôle des paiements est réalisé par la Direction ou la DO.");
        }
        Depense d = charger(id);
        d.controler(access.utilisateur().id(), clock.instant(), justificatifs.existsByDepenseId(id), commentaire);
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Paiement contrôlé", libelle(d)));
        return d;
    }

    public Justificatif joindre(UUID id, MultipartFile fichier) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.S);
        Depense d = charger(id);
        if (d.getStatut() == Depense.Statut.CONTROLEE || d.getStatut() == Depense.Statut.REFUSEE) {
            throw DomainException.etatInvalide("Dossier clos : aucun justificatif ne peut plus être ajouté.");
        }
        FichierStocke f = stockage.stocker(fichier);
        String nom = fichier.getOriginalFilename() == null ? "justificatif" : fichier.getOriginalFilename().replaceAll("[\\\\/\\r\\n\"]", "_");
        Justificatif j = justificatifs.save(new Justificatif(new Justificatif.Donnees(d.getId(), nom, f.typeMime(), f.taille(), f.sha256(), f.chemin(),
                access.utilisateur().id(), clock.instant())));
        events.publishEvent(AuditEvent.of(ModuleCode.ADMINISTRATION, "Justificatif joint", d.getReference() + " · " + nom));
        return j;
    }

    @Transactional(readOnly = true)
    public Fichier telecharger(UUID justificatifId) {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        Justificatif j = justificatifs.findById(justificatifId).orElseThrow(() -> DomainException.introuvable("Justificatif"));
        return new Fichier(j, stockage.lire(j.getCheminStockage()));
    }

    /** Actions possibles pour l'utilisateur courant — le front affiche les boutons (ou la raison du blocage). */
    public List<ActionPossible> actionsPossibles(Depense d, long seuil, boolean justificatifPresent) {
        CurrentUser u = access.utilisateur();
        List<ActionPossible> out = new ArrayList<>();
        Depense.Statut statut = d.getStatut();
        if (statut == Depense.Statut.DEMANDEE) {
            actionsValidation(d, seuil, u, out);
        } else if (statut == Depense.Statut.VALIDEE && u.peut(ModuleCode.ADMINISTRATION, ActionCode.G)) {
            out.add(u.id().equals(d.getValidePar())
                    ? ActionPossible.bloquee(ActionPossible.PAYER, "Séparation des tâches : le valideur ne paie pas.")
                    : ActionPossible.ok(ActionPossible.PAYER));
        } else if (statut == Depense.Statut.PAYEE && u.estProfil(Profils.DG, Profils.MANAGER, Profils.DO)) {
            out.add(actionControle(d, u, justificatifPresent));
        }
        return out;
    }

    /** Validation : jamais le demandeur ; DG sans plafond ; DO jusqu'au seuil DG. */
    private static void actionsValidation(Depense d, long seuil, CurrentUser u, List<ActionPossible> out) {
        if (d.getDemandePar().equals(u.id())) {
            out.add(ActionPossible.bloquee(ActionPossible.VALIDER, "Séparation des tâches : une autre personne valide."));
        } else if (u.peut(ModuleCode.ADMINISTRATION, ActionCode.V) || (u.estProfil(Profils.DO) && d.getMontant() <= seuil)) {
            out.add(ActionPossible.ok(ActionPossible.VALIDER));
            out.add(ActionPossible.ok(ActionPossible.REFUSER));
        } else if (u.estProfil(Profils.DO)) {
            out.add(ActionPossible.bloquee(ActionPossible.VALIDER, "Au-delà de " + fcfa(seuil) + " FCFA : validation DG."));
        }
    }

    /** Contrôle d'un paiement : indépendant de toutes les étapes précédentes, justificatif obligatoire. */
    private static ActionPossible actionControle(Depense d, CurrentUser u, boolean justificatifPresent) {
        if (d.estIntervenant(u.id())) {
            return ActionPossible.bloquee(ActionPossible.CONTROLER, "Séparation des tâches : contrôle indépendant requis.");
        }
        return justificatifPresent
                ? ActionPossible.ok(ActionPossible.CONTROLER)
                : ActionPossible.bloquee(ActionPossible.CONTROLER, "Justificatif manquant.");
    }

    public long seuilDg() {
        return parametres.montant(Parametres.SEUIL_DG);
    }

    @Transactional(readOnly = true)
    public Synthese synthese() {
        access.exiger(ModuleCode.ADMINISTRATION, ActionCode.C);
        Map<String, Map<String, Long>> parStatut = new LinkedHashMap<>();
        EnumSet.allOf(Depense.Statut.class).forEach(s -> parStatut.put(s.name(), Map.of("nombre", 0L, "total", 0L)));
        depenses.totauxParStatut().forEach(t -> parStatut.put(t.getStatut().name(), Map.of("nombre", t.getNombre(), "total", t.getTotal())));
        return new Synthese(parametres.montant(Parametres.SEUIL_DG), parStatut);
    }

    /**
     * DG : droit V. DO : arbitrage opérationnel par délégation, plafonné au seuil DG.
     */
    private void exigerPouvoirDeValidation(Depense d) {
        CurrentUser u = access.utilisateur();
        if (u.peut(ModuleCode.ADMINISTRATION, ActionCode.V)) {
            return;
        }
        if (u.estProfil(Profils.DO)) {
            long seuil = parametres.montant(Parametres.SEUIL_DG);
            if (d.getMontant() <= seuil) {
                return;
            }
            throw DomainException.regle("SEUIL_DG", "Au-delà de " + fcfa(seuil) + " FCFA, la validation revient à la DG.");
        }
        throw DomainException.regle("VALIDATION_DIRECTION", "La validation des dépenses revient à la DG (ou à la DO sous le seuil).");
    }

    private Depense charger(UUID id) {
        return depenses.findById(id).orElseThrow(() -> DomainException.introuvable("Dépense"));
    }

    private static String libelle(Depense d) {
        return d.getReference() + " · " + d.getObjet() + " · " + fcfa(d.getMontant()) + " FCFA";
    }

    private static String fcfa(long montant) {
        return String.format(Locale.FRANCE, "%,d", montant).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    public record ActionPossible(String action, boolean autorisee, String raison) {

        public static final String VALIDER = "valider";
        public static final String REFUSER = "refuser";
        public static final String PAYER = "payer";
        public static final String CONTROLER = "controler";

        static ActionPossible ok(String action) {
            return new ActionPossible(action, true, null);
        }

        static ActionPossible bloquee(String action, String raison) {
            return new ActionPossible(action, false, raison);
        }
    }

    public record Fichier(Justificatif meta, org.springframework.core.io.Resource contenu) {
    }

    public record Synthese(long seuilDg, Map<String, Map<String, Long>> parStatut) {
    }
}
