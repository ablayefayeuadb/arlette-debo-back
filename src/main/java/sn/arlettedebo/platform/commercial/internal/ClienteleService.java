package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.Clientele;
import sn.arlettedebo.platform.commercial.internal.domain.Abonnement;
import sn.arlettedebo.platform.commercial.internal.domain.AbonnementRepository;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisation;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisationRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.Referentiels.FormuleInfo;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Prospects, clientes et abonnements (accueil / caisse), dans la limite des périmètres de l'utilisateur.
 * <p>Consultation : caisse C (Manager, DG…) ; saisie : caisse S (Caissier, Chargé de clientèle, Vendeur boutique).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteleService implements Clientele {

    private final ClientRepository clients;
    private final AbonnementRepository abonnements;
    private final DemandeAutorisationRepository demandes;
    private final AccessPolicy access;
    private final Referentiels referentiels;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final FieldCrypto crypto;

    // ------------------------------------------------------------------ Fiches

    @Transactional(readOnly = true)
    public Page<Client> rechercher(String texte, Client.Statut statut, Pageable page) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        Set<String> perimetres = access.utilisateur().perimetres();
        String t = texte == null ? "" : texte.trim();
        if (t.isEmpty()) {
            return statut == null ? clients.findByPerimetreCodeInOrderByIdDesc(perimetres, page)
                    : clients.findByPerimetreCodeInAndStatutOrderByIdDesc(perimetres, statut, page);
        }
        // Téléphone : correspondance exacte (index unique) ; nom : recherche floue (index trigramme)
        if (t.chars().filter(Character::isDigit).count() >= 6) {
            String tel = normaliserTelephone(t);
            String cle = crypto.rechercheTelephone(tel);
            if (cle == null) {
                return Page.empty(page);
            }
            return new org.springframework.data.domain.PageImpl<>(clients.findByTelephoneRecherche(cle)
                    .filter(c -> perimetres.contains(c.getPerimetreCode())).stream().toList(), page, 1);
        }
        List<Client> trouves = clients.rechercher(perimetres, t).stream()
                .filter(c -> statut == null || c.getStatut() == statut).toList();
        return new org.springframework.data.domain.PageImpl<>(trouves, page, trouves.size());
    }

    @Transactional(readOnly = true)
    public Client lire(UUID id) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return charger(id);
    }

    public Client creer(Client.Donnees d) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        access.exigerPerimetre(d.perimetre());
        if (d.statut() == Client.Statut.ABONNEE) {
            throw DomainException.invalide("Le statut « abonnée » s'obtient par la vente d'un abonnement.");
        }
        String tel = normaliserTelephone(d.telephone());
        String cleTel = tel == null ? null : crypto.rechercheTelephone(tel);
        if (cleTel != null && clients.findByTelephoneRecherche(cleTel).isPresent()) {
            throw new DomainException(HttpStatus.CONFLICT, "DOUBLON", "Une fiche existe déjà avec ce numéro de téléphone.");
        }
        Client c = clients.save(new Client(new Client.Donnees(d.perimetre(), d.nom().trim(), tel, vide(d.email()), d.dateNaissance(),
                d.statut() == null ? Client.Statut.PROSPECT : d.statut(), vide(d.source()), d.consentementMarketing())));
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Fiche cliente créée", c.getNom() + " · " + c.getStatut()));
        return c;
    }

    public Client annoter(UUID id, String preferences, String notes) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        Client c = charger(id);
        c.annoter(vide(preferences), vide(notes));
        return c;
    }

    // ------------------------------------------------------------------ Abonnements

    @Transactional(readOnly = true)
    public List<Abonnement> abonnements(UUID clientId) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        charger(clientId);
        return abonnements.findByClientIdOrderByDebutDesc(clientId);
    }

    @Transactional(readOnly = true)
    public List<Abonnement> abonnements(Collection<UUID> clientIds) {
        access.exiger(ModuleCode.CAISSE, ActionCode.C);
        return clientIds.isEmpty() ? List.of() : abonnements.findByClientIdInOrderByDebutDesc(clientIds);
    }

    /** Vente d'une formule du périmètre de la cliente ; l'encaissement correspondant est saisi en caisse. */
    public Abonnement vendre(UUID clientId, String formuleCode) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        Client c = charger(clientId);
        FormuleInfo f = referentiels.formule(formuleCode)
                .orElseThrow(() -> DomainException.invalide("Formule inconnue ou inactive : " + formuleCode));
        if (!f.perimetre().equals(c.getPerimetreCode())) {
            throw DomainException.invalide("Cette formule n'est pas proposée dans le périmètre de la cliente.");
        }
        Abonnement a = abonnements.save(new Abonnement(c.getId(), f.code(), LocalDate.now(clock), f.dureeJours(), f.nbSeances(),
                access.utilisateur().id()));
        c.devenirAbonnee();
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Abonnement vendu", c.getNom() + " · " + f.libelle() + " · " + f.prix() + " FCFA"));
        return a;
    }

    public Abonnement consommerSeance(UUID abonnementId) {
        access.exiger(ModuleCode.CAISSE, ActionCode.S);
        Abonnement a = abonnements.findById(abonnementId).orElseThrow(() -> DomainException.introuvable("Abonnement"));
        Client c = charger(a.getClientId());
        a.consommer(LocalDate.now(clock));
        c.enregistrerVisite(LocalDate.now(clock), null);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Séance d'abonnement consommée", c.getNom() + " · " + a.getFormuleCode()));
        return a;
    }

    // ------------------------------------------------------------------ API publique (caisse)

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> ficheParTelephone(String telephone, String perimetre) {
        String cle = crypto.rechercheTelephone(normaliserTelephone(telephone));
        if (cle == null) {
            return Optional.empty();
        }
        return clients.findByTelephoneRecherche(cle)
                .filter(c -> c.getPerimetreCode().equals(perimetre))
                .map(Client::getId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FicheAbonnee> abonneeParTelephone(String telephone, String perimetre) {
        String cle = crypto.rechercheTelephone(normaliserTelephone(telephone));
        if (cle == null) {
            return Optional.empty();
        }
        return clients.findByTelephoneRecherche(cle)
                .filter(c -> c.getPerimetreCode().equals(perimetre))
                .filter(c -> c.getStatut() == Client.Statut.ABONNEE)
                .map(c -> new FicheAbonnee(c.getId(), c.getNom()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String exigerCliente(UUID clientId, String perimetre) {
        Client c = clients.findById(clientId).orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
        if (!c.getPerimetreCode().equals(perimetre)) {
            throw DomainException.invalide("La cliente n'est pas rattachée à ce périmètre.");
        }
        return c.getNom();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrerVisite(UUID clientId, LocalDate jour, UUID coiffeuseId) {
        clients.findById(clientId).ifPresent(c -> c.enregistrerVisite(jour, coiffeuseId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long appliquerGeste(UUID demandeAutorisationId, String perimetre) {
        DemandeAutorisation d = demandes.findById(demandeAutorisationId).orElseThrow(() -> DomainException.introuvable("Demande d'autorisation"));
        if (!d.getPerimetreCode().equals(perimetre)) {
            throw DomainException.invalide("Ce geste commercial concerne un autre périmètre.");
        }
        d.appliquer(clock.instant());
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Décision commerciale appliquée", d.getTypeCode() + " · " + d.getClienteNom()));
        return d.getMontant();
    }

    // ------------------------------------------------------------------ Outils

    private Client charger(UUID id) {
        Client c = clients.findById(id).orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
        access.exigerPerimetre(c.getPerimetreCode());
        return c;
    }

    /** Numéro sénégalais normalisé : chiffres seuls, indicatif +221 retiré (« 77 123 45 67 » → « 771234567 »). */
    static String normaliserTelephone(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String chiffres = brut.replaceAll("\\D", "");
        if (chiffres.startsWith("221") && chiffres.length() == 12) {
            chiffres = chiffres.substring(3);
        }
        if (chiffres.length() < 7 || chiffres.length() > 15) {
            throw DomainException.invalide("Numéro de téléphone invalide.");
        }
        return chiffres;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
