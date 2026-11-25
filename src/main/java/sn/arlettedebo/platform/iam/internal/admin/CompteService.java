package sn.arlettedebo.platform.iam.internal.admin;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.AccessPolicy;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissementRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;
import sn.arlettedebo.platform.iam.internal.security.PasswordPolicy;
import sn.arlettedebo.platform.iam.internal.security.RightsService;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.TypeReferentiel;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.shared.sms.SmsMessages;
import sn.arlettedebo.platform.shared.mail.MailService;
import sn.arlettedebo.platform.shared.sms.SmsService;
import sn.arlettedebo.platform.shared.telephone.Telephones;
import org.springframework.beans.factory.annotation.Value;

/**
 * Gestion des comptes.
 * <ul>
 *   <li>La DG et la Manager créent, modifient, désactivent et attribuent tous les profils ;</li>
 *   <li>la RH (droit {@code rh:G}) crée les comptes Coiffeur — les autres profils sont attribués par la direction ;</li>
 *   <li>un seul caissier par salon : un salon n'a qu'un compte actif qui encaisse (Caisse, Clientèle) ;
 *       le vendeur boutique n'encaisse pas (facture → paiement au caissier) ;
 *       un même caissier peut en revanche tenir plusieurs salons ;</li>
 *   <li>personne ne peut désactiver son propre compte.</li>
 * </ul>
 */
@Service
@Transactional
public class CompteService {

    private final UtilisateurRepository utilisateurs;
    private final ProfilRepository profils;
    private final JetonRafraichissementRepository jetons;
    private final PasswordEncoder encoder;
    private final PasswordPolicy politique;
    private final Referentiels referentiels;
    private final RightsService rights;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SmsService sms;
    private final MailService mail;
    private final FieldCrypto crypto;
    private final String publicAppUrl;
    /** Proxy pour appels @Transactional via Spring (pas via {@code this}). */
    private final CompteService self;

    CompteService(UtilisateurRepository utilisateurs, ProfilRepository profils, JetonRafraichissementRepository jetons,
            PasswordEncoder encoder, PasswordPolicy politique, Referentiels referentiels, RightsService rights,
            AccessPolicy access, ApplicationEventPublisher events, Clock clock, SmsService sms, MailService mail,
            FieldCrypto crypto, @Value("${app.public-app-url:https://arlettedebo.sn}") String publicAppUrl,
            @Lazy CompteService self) {
        this.utilisateurs = utilisateurs;
        this.profils = profils;
        this.jetons = jetons;
        this.encoder = encoder;
        this.politique = politique;
        this.referentiels = referentiels;
        this.rights = rights;
        this.access = access;
        this.events = events;
        this.clock = clock;
        this.sms = sms;
        this.mail = mail;
        this.crypto = crypto;
        this.publicAppUrl = publicAppUrl == null || publicAppUrl.isBlank() ? "https://arlettedebo.sn" : publicAppUrl.replaceAll("/$", "");
        this.self = self;
    }

    @Transactional(readOnly = true)
    public List<Utilisateur> lister() {
        return self.lister(FiltreComptes.AUCUN);
    }

    /**
     * Comptes visibles (direction : tous), filtrés puis triés par id décroissant (plus récents d’abord).
     * Liste bornée (personnel de l'entreprise) : filtrée en mémoire, car le salon effectif d'un compte
     * dépend de son profil quand il n'a pas de salon propre.
     */
    @Transactional(readOnly = true)
    public List<Utilisateur> lister(FiltreComptes f) {
        java.time.Instant maintenant = clock.instant();
        String texte = normaliser(f.texte());
        return utilisateurs.findAllByOrderByIdDesc().stream()
                .filter(u -> !Profils.ABONNEE.equals(u.getProfilCode()))
                .filter(u -> texte.isEmpty() || normaliser(u.getNom() + " " + u.getEmail()).contains(texte))
                .filter(u -> f.profil() == null || f.profil().isBlank() || f.profil().equals(u.getProfilCode()))
                .filter(u -> f.salon() == null || f.salon().isBlank() || salonsDe(u).contains(f.salon()))
                .filter(u -> f.statut() == null || switch (f.statut()) {
                    case ACTIF -> u.isActif();
                    case DESACTIVE -> !u.isActif();
                    case VERROUILLE -> u.estVerrouille(maintenant);
                    case PROVISOIRE -> u.isDoitChangerMdp();
                })
                .toList();
    }

    /**
     * Compte portail abonnée (hors matrice staff) : appelé depuis le dossier commerciale.
     * @return compte créé et mot de passe provisoire (affiché / SMS une seule fois)
     */
    public CompteCree creerAccesAbonnee(String nom, String email, String telephone, String perimetre) {
        if (!profils.existsById(Profils.ABONNEE)) {
            throw DomainException.invalide("Profil ABONNEE absent : migration V52 requise.");
        }
        if (utilisateurs.existsByEmailRecherche(crypto.rechercheEmail(email))) {
            throw new DomainException(org.springframework.http.HttpStatus.CONFLICT, "EMAIL_EXISTANT",
                    "Un compte existe déjà avec cet e-mail.");
        }
        String tel = Telephones.masquer(telephone);
        if (tel == null) {
            throw DomainException.invalide("Le téléphone de la cliente est obligatoire pour ouvrir l'accès.");
        }
        Set<String> salons = perimetre == null || perimetre.isBlank() ? Set.of() : Set.of(perimetre);
        verifierPerimetres(salons);
        String mdp = politique.genererProvisoire();
        politique.verifier(mdp, email);
        Utilisateur u = new Utilisateur(nom.trim(), email, encoder.encode(mdp), Profils.ABONNEE, salons, true, tel);
        utilisateurs.save(u);
        events.publishEvent(AuditEvent.of(ModuleCode.CAISSE, "Accès portail abonnée créé", u.getNom() + " · " + tel));
        utilisateurs.flush();
        events.publishEvent(new sn.arlettedebo.platform.iam.CompteCree(u.getId()));
        String loginUrl = publicAppUrl + "/login";
        mail.accesAbonnee(u.getEmail(), u.getNom(), mdp, loginUrl);
        sms.envoyer(tel, SmsMessages.accesAbonnee(u.getEmail(), mdp, loginUrl));
        return new CompteCree(u, mdp);
    }

    /** Minuscules sans accents : « ndeye » trouve « Ndèye ». */
    private static String normaliser(String t) {
        return t == null ? "" : java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Critères de recherche des comptes (tous facultatifs). */
    public record FiltreComptes(String texte, String profil, String salon, StatutCompte statut) {
        public static final FiltreComptes AUCUN = new FiltreComptes(null, null, null, null);
    }

    public enum StatutCompte { ACTIF, DESACTIVE, VERROUILLE, PROVISOIRE }

    /** @return compte créé et mot de passe provisoire (affiché une seule fois) */
    public CompteCree creer(String nom, String email, String profil, Set<String> perimetres, String motDePasse, String telephone) {
        CurrentUser moi = access.utilisateur();
        if (Profils.ABONNEE.equals(profil)) {
            throw DomainException.regle("PROFIL_PORTAIL",
                    "Le compte abonnée s'ouvre depuis le dossier (bouton « Ouvrir l'accès »), pas depuis les comptes staff.");
        }
        if (!direction(moi) && !(moi.peut(ModuleCode.RH, ActionCode.G) && Profils.COIFFEUR.equals(profil))) {
            throw DomainException.regle("ATTRIBUTION_PROFIL_DG",
                    "Seule la direction attribue ce profil ; la RH crée uniquement des comptes Coiffeur.");
        }
        if (!profils.existsById(profil)) {
            throw DomainException.invalide("Profil inconnu : " + profil);
        }
        if (utilisateurs.existsByEmailRecherche(crypto.rechercheEmail(email))) {
            throw new DomainException(org.springframework.http.HttpStatus.CONFLICT, "EMAIL_EXISTANT", "Un compte existe déjà avec cet e-mail.");
        }
        String tel = Telephones.masquer(telephone);
        if (tel == null) {
            throw DomainException.invalide("Le téléphone est obligatoire pour notifier le compte par SMS.");
        }
        verifierPerimetres(perimetres);
        String mdp = motDePasse == null || motDePasse.isBlank() ? politique.genererProvisoire() : motDePasse;
        politique.verifier(mdp, email);
        exigerCaissierUnique(null, profil, perimetres);
        Utilisateur u = new Utilisateur(nom.trim(), email, encoder.encode(mdp), profil, perimetres, true, tel);
        utilisateurs.save(u);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Compte créé", u.getNom() + " · " + profil + " · " + tel));
        utilisateurs.flush();
        events.publishEvent(new sn.arlettedebo.platform.iam.CompteCree(u.getId()));
        String provisoire = motDePasse == null || motDePasse.isBlank() ? mdp : mdp;
        sms.envoyer(tel, SmsMessages.compteCree(u.getEmail(), provisoire, publicAppUrl + "/login"));
        return new CompteCree(u, motDePasse == null || motDePasse.isBlank() ? mdp : null);
    }

    public Utilisateur changerProfil(UUID id, String profil) {
        exigerDirection();
        if (Profils.ABONNEE.equals(profil)) {
            throw DomainException.regle("PROFIL_PORTAIL",
                    "Le profil abonnée s'ouvre depuis le dossier, pas par changement de profil staff.");
        }
        if (!profils.existsById(profil)) {
            throw DomainException.invalide("Profil inconnu : " + profil);
        }
        Utilisateur u = charger(id);
        String ancien = u.getProfilCode();
        exigerCaissierUnique(u.getId(), profil, u.getPerimetres());
        u.changerProfil(profil);
        rights.invalider(id);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Profil modifié", u.getNom() + " : " + ancien + " → " + profil));
        return u;
    }

    public Utilisateur definirPerimetres(UUID id, Set<String> perimetres) {
        Utilisateur u = charger(id);
        exigerGestion(u);
        verifierPerimetres(perimetres);
        exigerCaissierUnique(u.getId(), u.getProfilCode(), perimetres);
        u.definirPerimetres(perimetres);
        // Les droits sont recalculés à chaque requête (cache invalidé) : effet immédiat, sans reconnexion.
        rights.invalider(id);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Périmètres modifiés",
                u.getNom() + " : " + (perimetres.isEmpty() ? "ceux du profil" : String.join(", ", new java.util.TreeSet<>(perimetres)))));
        return u;
    }

    /**
     * Désigne un vendeur boutique comme responsable d'une boutique autonome :
     * ajoute le périmètre à ses salons effectifs (propres, sinon ceux du profil).
     */
    public void rattacherBoutique(UUID utilisateurId, String perimetreBoutique) {
        Utilisateur u = charger(utilisateurId);
        if (!u.isActif()) {
            throw DomainException.regle("COMPTE_INACTIF", "Le vendeur boutique désigné est désactivé.");
        }
        if (!Profils.VENDEUR_BOUTIQUE.equals(u.getProfilCode())) {
            throw DomainException.invalide("Seul un vendeur boutique peut être désigné responsable d'une boutique.");
        }
        referentiels.exiger(TypeReferentiel.PERIMETRE, perimetreBoutique);
        Set<String> actuel = new java.util.LinkedHashSet<>(salonsDe(u));
        if (!actuel.add(perimetreBoutique)) {
            return;
        }
        u.definirPerimetres(actuel);
        rights.invalider(utilisateurId);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Responsable boutique désigné",
                u.getNom() + " → " + perimetreBoutique));
    }

    /** Ensemble vide = périmètres du profil ; sinon chaque code doit exister au référentiel. */
    private void verifierPerimetres(Set<String> perimetres) {
        for (String p : perimetres) {
            if (!referentiels.existe(TypeReferentiel.PERIMETRE, p)) {
                throw DomainException.invalide("Périmètre inconnu : " + p);
            }
        }
    }

    public Utilisateur desactiver(UUID id) {
        if (id.equals(access.utilisateur().id())) {
            throw DomainException.regle("AUTO_DESACTIVATION", "Vous ne pouvez pas désactiver votre propre compte.");
        }
        Utilisateur u = charger(id);
        exigerGestion(u);
        u.desactiver();
        jetons.revoquerTous(id, clock.instant());
        rights.invalider(id);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Compte désactivé", u.getNom()));
        return u;
    }

    public Utilisateur reactiver(UUID id) {
        Utilisateur u = charger(id);
        exigerGestion(u);
        exigerCaissierUnique(u.getId(), u.getProfilCode(), u.getPerimetres());
        u.reactiver();
        rights.invalider(id);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Compte réactivé", u.getNom()));
        return u;
    }

    public Utilisateur deverrouiller(UUID id) {
        Utilisateur u = charger(id);
        if (!direction(access.utilisateur()) && !access.peut(ModuleCode.RH, ActionCode.G)) {
            exigerGestion(u);
        }
        u.deverrouiller();
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Compte déverrouillé", u.getNom()));
        return u;
    }

    /** Génère un mot de passe provisoire et révoque toutes les sessions du compte. */
    public CompteCree reinitialiserMotDePasse(UUID id) {
        Utilisateur u = charger(id);
        exigerGestion(u);
        String mdp = politique.genererProvisoire();
        u.changerMotDePasse(encoder.encode(mdp), true);
        u.deverrouiller();
        jetons.revoquerTous(id, clock.instant());
        rights.invalider(id);
        events.publishEvent(AuditEvent.of(ModuleCode.RH, "Mot de passe réinitialisé", u.getNom()));
        return new CompteCree(u, mdp);
    }

    /** DG et Manager : tous les comptes. */
    private void exigerGestion(Utilisateur cible) {
        if (direction(access.utilisateur())) {
            return;
        }
        throw DomainException.regle("RESERVE_DG",
                "Action réservée à la Direction (compte " + cible.getNom() + ").");
    }

    /**
     * Un seul caissier par salon : refuse qu'un compte qui encaisse (création, changement de salons ou de profil,
     * réactivation) arrive sur un salon actif déjà tenu par un autre caissier actif.
     *
     * @param soi       compte concerné (exclu de la recherche), {@code null} à la création
     * @param salonsPropres salons propres du compte ; vide = salons de son profil
     */
    private void exigerCaissierUnique(UUID soi, String profil, Set<String> salonsPropres) {
        if (!Profils.CAISSIERS.contains(profil)) {
            return;
        }
        Set<String> actifs = referentiels.perimetresActifs();
        Set<String> cibles = new java.util.HashSet<>(salonsPropres.isEmpty()
                ? profils.findById(profil).map(p -> Set.copyOf(p.getPerimetres())).orElse(Set.of())
                : salonsPropres);
        cibles.retainAll(actifs);
        for (Utilisateur autre : utilisateurs.findAllByActifTrueOrderByNomAsc()) {
            if (autre.getId().equals(soi) || !Profils.CAISSIERS.contains(autre.getProfilCode())) {
                continue;
            }
            for (String salon : salonsDe(autre)) {
                if (cibles.contains(salon)) {
                    String nom = referentiels.libelle(TypeReferentiel.PERIMETRE, salon).orElse(salon);
                    throw DomainException.regle("UN_CAISSIER_PAR_SALON", "« " + nom + " » a déjà un caissier : " + autre.getNom()
                            + ". Retirez-le d'abord de ce salon ou désactivez son compte.");
                }
            }
        }
    }

    /** Salons d'un compte : les siens, sinon ceux de son profil. */
    private Set<String> salonsDe(Utilisateur u) {
        return u.getPerimetres().isEmpty()
                ? profils.findById(u.getProfilCode()).map(p -> Set.copyOf(p.getPerimetres())).orElse(Set.of())
                : Set.copyOf(u.getPerimetres());
    }

    private static boolean direction(CurrentUser u) {
        return u.estProfil(Profils.DG, Profils.MANAGER);
    }

    private void exigerDirection() {
        if (!direction(access.utilisateur())) {
            throw DomainException.regle("RESERVE_DG", "Action réservée à la Direction.");
        }
    }

    private Utilisateur charger(UUID id) {
        return utilisateurs.findById(id).orElseThrow(() -> DomainException.introuvable("Compte"));
    }

    public record CompteCree(Utilisateur utilisateur, String motDePasseProvisoire) {
    }
}
