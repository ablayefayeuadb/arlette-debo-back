package sn.arlettedebo.platform.iam.internal.auth;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissement;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissementRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;
import sn.arlettedebo.platform.iam.internal.security.LoginRateLimiter;
import sn.arlettedebo.platform.iam.internal.security.PasswordPolicy;
import sn.arlettedebo.platform.iam.internal.security.RightsService;
import sn.arlettedebo.platform.iam.internal.security.RightsService.EffectiveRights;
import sn.arlettedebo.platform.iam.internal.security.SecurityProperties;
import sn.arlettedebo.platform.iam.internal.security.TokenService;
import sn.arlettedebo.platform.iam.internal.security.TokenService.JetonAcces;
import sn.arlettedebo.platform.iam.internal.security.TokenService.JetonRafraichissementEmis;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.event.AuditEvent;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Cycle de vie des sessions : connexion, rotation des jetons, déconnexion, changement de mot de passe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurs;
    private final JetonRafraichissementRepository jetons;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final RightsService rights;
    private final PasswordPolicy politique;
    private final LoginRateLimiter limiteur;
    private final SecurityProperties props;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final FieldCrypto crypto;

    /** Haché réel (même coût BCrypt) : un e-mail inconnu coûte le même temps qu'un mauvais mot de passe. */
    private String hashFactice;

    @PostConstruct
    void initialiser() {
        hashFactice = encoder.encode(UUID.randomUUID().toString());
    }

    /** Les échecs doivent être enregistrés même si la connexion est refusée : pas de rollback sur erreur métier. */
    @Transactional(noRollbackFor = DomainException.class)
    public Session connecter(String email, String motDePasse, String ip, String agent) {
        limiteur.verifier(ip);
        Instant maintenant = clock.instant();
        String cleEmail = crypto.rechercheEmail(email == null ? "" : email);
        Utilisateur u = cleEmail == null ? null : utilisateurs.findByEmailRecherche(cleEmail).orElse(null);
        if (u == null) {
            encoder.matches(motDePasse == null ? "" : motDePasse, hashFactice);
            throw identifiantsInvalides();
        }
        if (u.estVerrouille(maintenant)) {
            throw new DomainException(HttpStatus.LOCKED, "COMPTE_VERROUILLE",
                    "Compte temporairement verrouillé après plusieurs échecs. Réessayez plus tard ou contactez la Direction.");
        }
        if (!encoder.matches(motDePasse == null ? "" : motDePasse, u.getMotDePasseHash()) || !u.isActif()) {
            if (u.isActif() && u.enregistrerEchec(props.login().maxAttempts(), props.login().lockDuration(), maintenant)) {
                events.publishEvent(new AuditEvent(ModuleCode.ADMINISTRATION, "Compte verrouillé",
                        "Trop d'échecs de connexion depuis " + ip, u.getId(), u.getNom()));
            }
            throw identifiantsInvalides();
        }
        u.enregistrerConnexion(maintenant);
        rights.invalider(u.getId());
        events.publishEvent(new AuditEvent(ModuleCode.DASHBOARD, "Connexion", ip, u.getId(), u.getNom()));
        return ouvrirSession(u, UUID.randomUUID(), ip, agent);
    }

    /**
     * Rotation : le jeton présenté est consommé et remplacé. S'il avait déjà été consommé,
     * c'est un rejeu (vol probable) : toute la famille est révoquée et les JWT du compte invalidés.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public Session rafraichir(String brut, String ip, String agent) {
        if (brut == null || brut.isBlank()) {
            throw sessionInvalide();
        }
        Instant maintenant = clock.instant();
        JetonRafraichissement jeton = jetons.findByEmpreinte(TokenService.empreinte(brut)).orElseThrow(AuthService::sessionInvalide);
        Utilisateur u = utilisateurs.findById(jeton.getUtilisateurId()).orElseThrow(AuthService::sessionInvalide);
        if (jeton.estRevoque()) {
            if (jeton.getRemplacePar() != null) {
                log.warn("Rejeu d'un jeton de rafraîchissement — famille {} révoquée", jeton.getFamille());
                // Modifier l'entité AVANT la requête de masse (qui vide le contexte si clear automatique)
                u.revoquerJetons();
                jetons.revoquerFamille(jeton.getFamille(), maintenant);
                rights.invalider(u.getId());
                events.publishEvent(new AuditEvent(ModuleCode.DASHBOARD, "Rejeu de jeton détecté",
                        "Session révoquée par sécurité (" + ip + ")", u.getId(), u.getNom()));
            }
            throw sessionInvalide();
        }
        if (jeton.estExpire(maintenant) || !u.isActif()) {
            throw sessionInvalide();
        }
        Session session = ouvrirSession(u, jeton.getFamille(), ip, agent);
        jeton.remplacer(session.rafraichissement().id(), maintenant);
        return session;
    }

    /** Déconnexion de la session courante (famille du jeton). */
    @Transactional
    public void deconnecter(String brut) {
        if (brut == null || brut.isBlank()) {
            return;
        }
        jetons.findByEmpreinte(TokenService.empreinte(brut))
                .ifPresent(j -> jetons.revoquerFamille(j.getFamille(), clock.instant()));
    }

    /** Déconnexion de tous les appareils : refresh révoqués + JWT invalidés via la version. */
    @Transactional
    public void deconnecterPartout(UUID utilisateurId) {
        Utilisateur u = utilisateurs.findById(utilisateurId).orElseThrow(() -> DomainException.introuvable("Utilisateur"));
        u.revoquerJetons();
        jetons.revoquerTous(utilisateurId, clock.instant());
        rights.invalider(utilisateurId);
        events.publishEvent(AuditEvent.of(ModuleCode.DASHBOARD, "Déconnexion de tous les appareils", u.getNom()));
    }

    /**
     * Toutes les sessions sont révoquées (jetons d'accès et de rafraîchissement) ; une nouvelle session est
     * ouverte pour l'appareil qui a fait le changement, afin que l'utilisateur ne soit pas déconnecté.
     */
    @Transactional
    public Session changerMotDePasse(UUID utilisateurId, String actuel, String nouveau, String ip, String agent) {
        Utilisateur u = utilisateurs.findById(utilisateurId).orElseThrow(() -> DomainException.introuvable("Utilisateur"));
        // Première connexion (provisoire) : l'utilisateur est déjà authentifié — pas besoin de ressaisir l'ancien MDP.
        if (!u.isDoitChangerMdp()) {
            if (actuel == null || actuel.isBlank()
                    || !encoder.matches(actuel, u.getMotDePasseHash())) {
                throw DomainException.regle("MOT_DE_PASSE_INCORRECT", "Mot de passe actuel incorrect.");
            }
        }
        if (encoder.matches(nouveau, u.getMotDePasseHash())) {
            throw DomainException.invalide("Le nouveau mot de passe doit être différent de l'actuel.");
        }
        politique.verifier(nouveau, u.getEmail());
        u.changerMotDePasse(encoder.encode(nouveau), false);
        jetons.revoquerTous(utilisateurId, clock.instant());
        rights.invalider(utilisateurId);
        events.publishEvent(AuditEvent.of(ModuleCode.DASHBOARD, "Mot de passe modifié", u.getNom()));
        utilisateurs.flush();
        return ouvrirSession(u, UUID.randomUUID(), ip, agent);
    }

    private Session ouvrirSession(Utilisateur u, UUID famille, String ip, String agent) {
        EffectiveRights droits = rights.calculer(u.getId()).orElseThrow(AuthService::sessionInvalide);
        JetonAcces acces = tokens.emettreAcces(droits.utilisateur(), u.getVersionJeton());
        JetonRafraichissementEmis refresh = tokens.emettreRafraichissement(u.getId(), famille, ip, agent);
        return new Session(acces, refresh, droits.utilisateur());
    }

    private static DomainException identifiantsInvalides() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "IDENTIFIANTS_INVALIDES", "E-mail ou mot de passe incorrect.");
    }

    private static DomainException sessionInvalide() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDE", "Session expirée ou révoquée : reconnectez-vous.");
    }

    public record Session(JetonAcces acces, JetonRafraichissementEmis rafraichissement, CurrentUser utilisateur) {
    }
}
