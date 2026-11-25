package sn.arlettedebo.platform.caisse.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.caisse.internal.domain.Encaissement;
import sn.arlettedebo.platform.caisse.internal.domain.EncaissementRepository;
import sn.arlettedebo.platform.caisse.internal.domain.Facture;
import sn.arlettedebo.platform.caisse.internal.domain.FactureRepository;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository.TotalSession;
import sn.arlettedebo.platform.commercial.Clientele;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.pilotage.Periode;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Caisse : journée de caisse (ouverture, encaissements, annulations, clôture), consultation des sessions
 * et contrôle indépendant des clôtures.
 * <p>Règle « Séparation des tâches de caisse » : le contrôle journalier est fait par une personne
 * qui n'est intervenue ni à l'ouverture, ni sur les encaissements, ni à la clôture, et qui consulte
 * la caisse sans restriction (DG, DO, Manager, Administration habilitée).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CaisseService {

    private static final String FCFA = " FCFA";

    private final SessionCaisseRepository sessions;
    private final EncaissementRepository encaissements;
    private final FactureRepository factures;
    private final Clientele clientele;
    private final Effectifs effectifs;
    private final Referentiels referentiels;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Synthèse des clôtures d'une période : agrégats SQL, sans charger les sessions. */
    @Transactional(readOnly = true)
    public SessionCaisseRepository.SyntheseClotures synthese(Periode p, String perimetre) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return sessions.synthese(perimetres(perimetre), p.du(), p.au());
    }

    /** Sessions d'une période, paginées en base (jour décroissant, puis salon). */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SessionCaisse> lister(Periode p, String perimetre, org.springframework.data.domain.Pageable page) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return sessions.findByPerimetreCodeInAndJourBetween(perimetres(perimetre), p.du(), p.au(), page);
    }

    @Transactional(readOnly = true)
    public SessionCaisse lire(UUID id) {
        return charger(id);
    }

    /** Lecture contrôlée (droit + périmètre), réutilisée dans la transaction courante. */
    private SessionCaisse charger(UUID id) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        SessionCaisse s = sessions.findWithEncaissementsById(id).orElseThrow(() -> DomainException.introuvable("Session de caisse"));
        access.exigerPerimetre(s.getPerimetreCode());
        return s;
    }

    @Transactional(readOnly = true)
    public Map<UUID, TotalSession> totaux(Collection<UUID> ids) {
        return ids.isEmpty() ? java.util.Collections.emptyMap() : sessions.totaux(ids).stream().collect(Collectors.toMap(TotalSession::getSessionId, t -> t));
    }

    /** Raison du refus, ou {@code null} si l'utilisateur courant peut contrôler cette clôture. */
    public String raisonRefusControle(SessionCaisse s) {
        CurrentUser u = access.utilisateur();
        if (s.getStatut() != SessionCaisse.Statut.CLOTUREE) {
            return "Session non clôturée.";
        }
        if (s.getControlePar() != null) {
            return "Déjà contrôlée.";
        }
        if (!u.couvre(s.getPerimetreCode())) {
            return "Hors de votre périmètre.";
        }
        if (!u.peut(ModuleCode.CAISSE, ActionCode.C) || u.restriction(ModuleCode.CAISSE) != null && !u.restriction(ModuleCode.CAISSE).isBlank()) {
            return "Profil non habilité au contrôle de caisse.";
        }
        if (s.intervenants().contains(u.id())) {
            return "Vous êtes intervenu(e) sur cette caisse : contrôle indépendant requis.";
        }
        return null;
    }

    public SessionCaisse controler(UUID id, boolean conforme, String commentaire) {
        SessionCaisse s = charger(id);
        String refus = raisonRefusControle(s);
        if (refus != null) {
            throw DomainException.regle(refus.startsWith("Vous êtes") ? "SEPARATION_DES_TACHES" : "CONTROLE_CAISSE", refus);
        }
        if (!conforme && (commentaire == null || commentaire.isBlank())) {
            throw DomainException.invalide("Une anomalie doit être commentée.");
        }
        s.controler(access.utilisateur().id(), clock.instant(), conforme, commentaire);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, conforme ? "Clôture contrôlée — conforme" : "Clôture contrôlée — anomalie",
                s.getPerimetreCode() + " · " + s.getJour() + " · écart " + s.getEcart() + FCFA));
        return s;
    }

    // ------------------------------------------------------------------ Journée de caisse (caisse S)

    /** Session du jour d'un périmètre (vide si la caisse n'a pas encore été ouverte). */
    @Transactional(readOnly = true)
    public Optional<SessionCaisse> duJour(String perimetre) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        access.exigerPerimetre(perimetre);
        return sessions.findByPerimetreCodeAndJour(perimetre, aujourdhui())
                .flatMap(s -> sessions.findWithEncaissementsById(s.getId()));
    }

    /**
     * Sessions encore OUVERTES (jour même ou veille non clôturée), pour clôturer le lendemain : la caisse d'un jour
     * précédent bloque l'ouverture du jour tant qu'elle n'est pas clôturée. Périmètre facultatif (tous les miens).
     */
    @Transactional(readOnly = true)
    public List<SessionCaisse> ouvertes(String perimetre) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return sessions.findWithEncaissementsByPerimetreCodeInAndStatutOrderByJourAsc(perimetres(perimetre), SessionCaisse.Statut.OUVERTE);
    }

    /** Ouverture avec fond de caisse compté : une seule session par jour, et une seule OUVERTE par périmètre. */
    public SessionCaisse ouvrir(String perimetre, long fond) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        access.exigerPerimetre(perimetre);
        LocalDate jour = aujourdhui();
        sessions.findByPerimetreCodeAndStatut(perimetre, SessionCaisse.Statut.OUVERTE).ifPresent(ouverte -> {
            throw DomainException.etatInvalide(ouverte.getJour().equals(jour)
                    ? "La caisse est déjà ouverte aujourd'hui."
                    : "Une caisse du " + ouverte.getJour()
                            + " est encore ouverte : clôturez-la avant d'en ouvrir une nouvelle.");
        });
        sessions.findByPerimetreCodeAndJour(perimetre, jour).ifPresent(existante -> {
            throw DomainException.etatInvalide(existante.getStatut() == SessionCaisse.Statut.OUVERTE
                    ? "La caisse est déjà ouverte aujourd'hui."
                    : "La caisse du jour est clôturée : une nouvelle session ne peut être ouverte que demain.");
        });
        SessionCaisse s = sessions.save(new SessionCaisse(perimetre, jour, fond, access.utilisateur().id(), clock.instant()));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Ouverture de caisse", perimetre + " · fond " + fond + FCFA));
        return s;
    }

    /**
     * Encaissement : mode de paiement du référentiel, cliente et coiffeuse du périmètre, geste commercial
     * autorisé appliqué (le montant encaissé est alors le montant net, saisi par la caisse).
     */
    public Encaissement encaisser(UUID sessionId, NouvelEncaissement n) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        SessionCaisse s = charger(sessionId);
        referentiels.exiger(TypeReferentiel.MODE_PAIEMENT, n.modePaiement());
        String libelle = n.libelle().trim();
        if (n.clientId() != null) {
            libelle = libelle + " · " + clientele.exigerCliente(n.clientId(), s.getPerimetreCode());
        }
        if (n.coiffeuseId() != null && effectifs.coiffeuses(s.getPerimetreCode()).stream().noneMatch(c -> c.id().equals(n.coiffeuseId()))) {
            throw DomainException.invalide("Cette coiffeuse n'est pas affectée à ce salon.");
        }
        Encaissement e = s.encaisser(libelle, n.montant(), n.modePaiement(), access.utilisateur().id(), clock.instant());
        e.rattacher(null, n.clientId(), n.coiffeuseId(), n.demandeAutorisationId());
        e.annoter(n.note());
        if (n.demandeAutorisationId() != null) {
            long geste = clientele.appliquerGeste(n.demandeAutorisationId(), s.getPerimetreCode());
            e.annoter(joindre(n.note(), "geste commercial " + geste + FCFA));
        }
        if (n.clientId() != null) {
            clientele.enregistrerVisite(n.clientId(), s.getJour(), n.coiffeuseId());
        }
        encaissements.save(e);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Encaissement",
                s.getPerimetreCode() + " · " + libelle + " · " + n.montant() + " FCFA · " + n.modePaiement()));
        return e;
    }

    /** Annulation motivée, uniquement sur une caisse encore ouverte (gestion caisse : G). */
    public Encaissement annuler(UUID encaissementId, String motif) {
        access.exiger(ModuleCode.CAISSE, ActionCode.G);
        Encaissement e = encaissements.findById(encaissementId).orElseThrow(() -> DomainException.introuvable("Encaissement"));
        SessionCaisse s = charger(e.getSession().getId());
        if (s.getStatut() != SessionCaisse.Statut.OUVERTE) {
            throw DomainException.etatInvalide("Caisse clôturée : l'encaissement ne peut plus être annulé.");
        }
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("Le motif d'annulation est obligatoire.");
        }
        e.annuler(motif.trim());
        if (e.getFactureId() != null) {
            Facture f = factures.findById(e.getFactureId()).orElse(null);
            if (f != null && f.getStatut() == Facture.Statut.PAYEE) {
                f.reouvrirApresAnnulationEncaissement();
            }
        }
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Encaissement annulé",
                s.getPerimetreCode() + " · " + e.getLibelle() + " · " + e.getMontant() + " FCFA · motif : " + motif.trim()));
        return e;
    }

    /** Clôture : espèces comptées ; l'écart est calculé et la clôture part en contrôle indépendant. */
    public SessionCaisse cloturer(UUID sessionId, long montantCompte) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        if (montantCompte < 0) {
            throw DomainException.invalide("Le montant compté ne peut pas être négatif.");
        }
        SessionCaisse s = charger(sessionId);
        s.cloturer(montantCompte, access.utilisateur().id(), clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Clôture de caisse",
                s.getPerimetreCode() + " · " + s.getJour() + " · total " + s.total() + " FCFA · écart " + s.getEcart() + FCFA));
        return s;
    }

    /** Données d'un encaissement saisi en caisse. */
    public record NouvelEncaissement(String libelle, long montant, String modePaiement, UUID clientId, UUID coiffeuseId,
            UUID demandeAutorisationId, String note) {
    }

    private static String joindre(String note, String ajout) {
        return note == null || note.isBlank() ? ajout : note.trim() + " · " + ajout;
    }

    @Transactional(readOnly = true)
    public List<SessionCaisse> aControler() {
        return sessions.aControler(access.utilisateur().perimetres()).stream().filter(s -> raisonRefusControle(s) == null).toList();
    }

    private Set<String> perimetres(String perimetre) {
        Set<String> miens = access.utilisateur().perimetres();
        if (perimetre == null) {
            return miens;
        }
        access.exigerPerimetre(perimetre);
        return Set.of(perimetre);
    }

    LocalDate aujourdhui() {
        return LocalDate.now(clock);
    }
}
