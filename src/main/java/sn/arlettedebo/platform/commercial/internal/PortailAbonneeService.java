package sn.arlettedebo.platform.commercial.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.commercial.internal.domain.BilanTrimestrielAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.DossierAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.EcheanceAbonnementRepository;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonnee;
import sn.arlettedebo.platform.commercial.internal.domain.PhotoEvolutionAbonneeRepository;
import sn.arlettedebo.platform.commercial.internal.domain.SuiviSeanceAbonneeRepository;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.internal.admin.CompteService;
import sn.arlettedebo.platform.iam.internal.admin.CompteService.CompteCree;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Confirmation;
import sn.arlettedebo.platform.planning.internal.ReservationEnLigneService.Disponibilite;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.shared.storage.JustificatifStorage;

/**
 * Portail abonnée : lecture cloisonnée au dossier lié au compte connecté, plus prise de RDV
 * (identité reprise de la fiche). L'ouverture d'accès reste côté staff via {@link #ouvrirAcces}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PortailAbonneeService {

    private static final Set<RendezVous.Statut> RDV_EXCLUS =
            EnumSet.of(RendezVous.Statut.ANNULEE, RendezVous.Statut.ABSENTE);

    private final DossierAbonneeRepository dossiers;
    private final ClientRepository clients;
    private final SuiviSeanceAbonneeRepository suivis;
    private final PhotoEvolutionAbonneeRepository photos;
    private final EcheanceAbonnementRepository echeances;
    private final BilanTrimestrielAbonneeRepository bilans;
    private final RendezVousRepository rdvs;
    private final UtilisateurRepository utilisateurs;
    private final CompteService comptes;
    private final AccessPolicy access;
    private final Referentiels referentiels;
    private final JustificatifStorage storage;
    private final ReservationEnLigneService reservations;
    private final Clock clock;

    public record FichePortail(DossierAbonnee dossier, Client client) {
    }

    public record AccesOuvert(UUID utilisateurId, String email, String motDePasseProvisoire) {
    }

    public record StatutAcces(boolean ouvert, UUID utilisateurId, String email, boolean actif) {
    }

    // ------------------------------------------------------------------ Staff : ouvrir / statut

    /** Ouvre l'accès portail pour un dossier (compte ABONNEE + e-mail / SMS). */
    public AccesOuvert ouvrirAcces(UUID dossierId, String emailForce) {
        DossierAbonnee dossier = dossierStaff(dossierId);
        if (dossier.getUtilisateurId() != null) {
            throw DomainException.regle("ACCES_EXISTANT", "Ce dossier a déjà un accès portail.");
        }
        Client client = clients.findById(dossier.getClientId())
                .orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
        String email = resoudreEmail(client, emailForce);
        client.definirEmail(email);
        String nom = nomCompte(dossier, client);
        CompteCree cree = comptes.creerAccesAbonnee(nom, email, client.getTelephone(), dossier.getPerimetreCode());
        dossier.lierCompte(cree.utilisateur().getId());
        return new AccesOuvert(cree.utilisateur().getId(), cree.utilisateur().getEmail(), cree.motDePasseProvisoire());
    }

    @Transactional(readOnly = true)
    public StatutAcces statutAcces(UUID dossierId) {
        DossierAbonnee dossier = dossierStaff(dossierId);
        if (dossier.getUtilisateurId() == null) {
            return new StatutAcces(false, null, null, false);
        }
        Utilisateur u = utilisateurs.findById(dossier.getUtilisateurId()).orElse(null);
        if (u == null) {
            return new StatutAcces(false, dossier.getUtilisateurId(), null, false);
        }
        return new StatutAcces(true, u.getId(), u.getEmail(), u.isActif());
    }

    // ------------------------------------------------------------------ Portail : lecture cloisonnée

    @Transactional(readOnly = true)
    public FichePortail maFiche() {
        return fiche(monDossier());
    }

    @Transactional(readOnly = true)
    public Page<SuiviSeanceAbonneeRepository.LignePortail> mesSuivis(Pageable page) {
        return suivis.lignesPortail(monRepere().id(), page);
    }

    @Transactional(readOnly = true)
    public Page<EcheanceAbonnementRepository.LignePortail> mesEcheances(Pageable page) {
        return echeances.lignesPortail(monRepere().id(), page);
    }

    /** Bilans de soins trimestriels de mon dossier (lecture seule, page SQL). */
    @Transactional(readOnly = true)
    public Page<BilanTrimestrielAbonneeRepository.LignePortail> mesBilans(Pageable page) {
        return bilans.lignesPortail(monRepere().id(), page);
    }

    /** Photos des séances de la page courante (une requête {@code IN}, colonnes utiles), groupées par suivi. */
    @Transactional(readOnly = true)
    public Map<UUID, List<PhotoEvolutionAbonneeRepository.Ligne>> photosParSuivis(Collection<UUID> suiviIds) {
        if (suiviIds == null || suiviIds.isEmpty()) {
            return Map.of();
        }
        return photos.lignesDuDossierParSuivis(monRepere().id(), suiviIds).stream()
                .collect(Collectors.groupingBy(PhotoEvolutionAbonneeRepository.Ligne::suiviId));
    }

    /** Photos de mon dossier, paginées en SQL. */
    @Transactional(readOnly = true)
    public Page<PhotoEvolutionAbonneeRepository.Ligne> mesPhotos(Pageable page) {
        return photos.lignesDuDossier(monRepere().id(), page);
    }

    @Transactional(readOnly = true)
    public AbonneesService.PhotoFichier telechargerPhoto(UUID photoId) {
        PhotoEvolutionAbonnee p = photos.findByIdAndDossierId(photoId, monRepere().id())
                .orElseThrow(() -> DomainException.introuvable("Photo"));
        return new AbonneesService.PhotoFichier(p, storage.lire(p.getCheminStockage()));
    }

    @Transactional(readOnly = true)
    public Page<RendezVousRepository.LigneAVenir> mesRdvsAVenir(Pageable page) {
        return rdvs.aVenirDeLaCliente(monRepere().clientId(), LocalDate.now(clock), RDV_EXCLUS, page);
    }

    /** Libellés d'un lot de prestations (une requête {@code IN}) : code → libellé. */
    @Transactional(readOnly = true)
    public Map<String, String> libellesPrestations(Collection<String> codes) {
        return referentiels.libellesPrestations(codes);
    }

    /** Prestations réservables de mon salon (filtre SQL sur le salon). */
    @Transactional(readOnly = true)
    public List<Referentiels.PrestationInfo> mesPrestations() {
        return reservations.catalogue(monRepere().perimetre());
    }

    @Transactional(readOnly = true)
    public List<Disponibilite> mesDisponibilites(LocalDate jour, String prestation) {
        return reservations.disponibilites(monRepere().perimetre(), jour, prestation);
    }

    @Transactional
    public Confirmation reserver(LocalDate jour, LocalTime heure, String prestation, String note, String ip) {
        FichePortail f = maFiche();
        String nom = nomCompte(f.dossier(), f.client());
        String telephone = f.client().getTelephone();
        if (telephone == null || telephone.isBlank()) {
            throw DomainException.invalide("Votre fiche n'a pas de téléphone : contactez le salon.");
        }
        return reservations.reserver(new ReservationEnLigneService.Demande(
                f.dossier().getPerimetreCode(), jour, heure, prestation, nom, telephone, note, "ABONNEE"), ip);
    }

    // ------------------------------------------------------------------ Interne

    /** Repère léger du dossier du compte connecté (id, fiche cliente, salon) : suffisant pour les listes. */
    private DossierAbonneeRepository.Repere monRepere() {
        UUID uid = exigerProfilAbonnee();
        return dossiers.repereParUtilisateur(uid)
                .orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
    }

    /** Dossier complet : uniquement pour la fiche et la prise de RDV (prénom, profil). */
    private DossierAbonnee monDossier() {
        UUID uid = exigerProfilAbonnee();
        return dossiers.findByUtilisateurId(uid)
                .orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
    }

    private UUID exigerProfilAbonnee() {
        if (!Profils.ABONNEE.equals(access.utilisateur().profil())) {
            throw DomainException.regle("PORTAIL_ABONNEE", "Réservé au portail abonnée.");
        }
        return access.utilisateur().id();
    }

    private DossierAbonnee dossierStaff(UUID id) {
        DossierAbonnee d = dossiers.findById(id).orElseThrow(() -> DomainException.introuvable("Dossier abonnée"));
        access.exigerPerimetre(d.getPerimetreCode());
        return d;
    }

    private FichePortail fiche(DossierAbonnee d) {
        Client c = clients.findById(d.getClientId()).orElseThrow(() -> DomainException.introuvable("Fiche cliente"));
        return new FichePortail(d, c);
    }

    private static String resoudreEmail(Client client, String emailForce) {
        String force = emailForce == null ? null : emailForce.trim().toLowerCase();
        if (force != null && !force.isBlank()) {
            if (!force.contains("@") || force.length() < 5) {
                throw DomainException.invalide("E-mail invalide.");
            }
            return force;
        }
        String email = client.getEmail();
        if (email == null || email.isBlank()) {
            throw DomainException.invalide(
                    "Indiquez un e-mail : il sera enregistré sur la fiche et servi d’identifiant de connexion.");
        }
        return email.trim().toLowerCase();
    }

    private static String nomCompte(DossierAbonnee dossier, Client client) {
        if (dossier.getPrenom() != null && !dossier.getPrenom().isBlank()) {
            String nom = client.getNom() == null ? "" : client.getNom().trim();
            if (nom.toLowerCase().startsWith(dossier.getPrenom().trim().toLowerCase())) {
                return nom.isBlank() ? dossier.getPrenom().trim() : nom;
            }
            return (dossier.getPrenom().trim() + " " + nom).trim();
        }
        return client.getNom() == null || client.getNom().isBlank() ? "Abonnée" : client.getNom().trim();
    }
}
