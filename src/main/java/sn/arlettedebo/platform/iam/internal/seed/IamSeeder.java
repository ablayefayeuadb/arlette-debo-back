package sn.arlettedebo.platform.iam.internal.seed;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.internal.domain.Delegation;
import sn.arlettedebo.platform.iam.internal.domain.Delegation.DroitDelegue;
import sn.arlettedebo.platform.iam.internal.domain.DelegationRepository;
import sn.arlettedebo.platform.iam.internal.domain.ProfilRepository;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;
import sn.arlettedebo.platform.iam.internal.security.PasswordPolicy;
import sn.arlettedebo.platform.shared.config.SeedProperties;
import sn.arlettedebo.platform.shared.crypto.FieldCrypto;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Seeder IAM — idempotent : crée un utilisateur pour chacun des 11 profils de la matrice
 * (s'il n'existe pas déjà) ainsi que les deux relais prévus par les règles de gestion.
 * Activé uniquement avec {@code app.seed.enabled=true}.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
class IamSeeder implements ApplicationRunner {

    /** Un compte par profil : profil → (nom, e-mail, périmètres propres éventuels). */
    static final List<CompteInitial> COMPTES = List.of(
            new CompteInitial(Profils.DG, "Direction Générale", "dg@yopmail.com", Set.of()),
            new CompteInitial(Profils.DO, "Directeur des Opérations", "do@yopmail.com", Set.of()),
            new CompteInitial(Profils.MANAGER, "Manager des salons", "manager@yopmail.com", Set.of()),
            new CompteInitial(Profils.COORDINATION, "Coordination", "coordination@yopmail.com", Set.of()),
            new CompteInitial(Profils.CAISSIER, "Caissier", "caissier@yopmail.com", Set.of()),
            new CompteInitial(Profils.CHARGE_CLIENTELE, "Chargé clientèle", "clientele@yopmail.com", Set.of()),
            new CompteInitial(Profils.COIFFEUR, "Coiffeur", "coiffeur@yopmail.com", Set.of("SKY")),
            new CompteInitial(Profils.VENDEUR_BOUTIQUE, "Responsable boutique", "boutique@yopmail.com", Set.of()),
            new CompteInitial(Profils.INVENTAIRE, "Inventaire", "inventaire@yopmail.com", Set.of()),
            new CompteInitial(Profils.COMMUNITY_MANAGER, "Community Manager", "community@yopmail.com", Set.of()),
            new CompteInitial(Profils.ADMINISTRATION, "Administration", "administration@yopmail.com", Set.of()));

    private final UtilisateurRepository utilisateurs;
    private final ProfilRepository profils;
    private final DelegationRepository delegations;
    private final PasswordEncoder encoder;
    private final PasswordPolicy politique;
    private final SeedProperties seed;
    private final FieldCrypto crypto;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seed.defaultPassword() == null || seed.defaultPassword().isBlank()) {
            throw new IllegalStateException("app.seed.default-password est obligatoire quand app.seed.enabled=true.");
        }
        politique.verifier(seed.defaultPassword(), null);
        long existants = profils.count();
        if (existants != COMPTES.size()) {
            log.warn("La matrice compte {} profils, le seeder en couvre {}.", existants, COMPTES.size());
        }
        String hash = encoder.encode(seed.defaultPassword());
        int crees = 0;
        for (CompteInitial c : COMPTES) {
            if (!utilisateurs.existsByEmailRecherche(crypto.rechercheEmail(c.email()))) {
                utilisateurs.save(new Utilisateur(c.nom(), c.email(), hash, c.profil(), c.perimetres(), seed.forcePasswordChange(), null));
                crees++;
            }
        }
        seedDelegations();
        log.info("Seeder IAM : {} compte(s) créé(s), {} profil(s) couvert(s).", crees, COMPTES.size());
    }

    private void seedDelegations() {
        if (delegations.findByCode("RELAIS_SKY").isEmpty()) {
            delegations.save(new Delegation("RELAIS_SKY", Profils.CHARGE_CLIENTELE, "Relais SKY",
                    "Binetou assure le relais accueil / caisse du Sky Club après formation.",
                    Set.of("SKY"), Set.of(), false));
        }
        if (delegations.findByCode("RELAIS_MANAGER").isEmpty()) {
            delegations.save(new Delegation("RELAIS_MANAGER", Profils.COORDINATION, "Relais Manager",
                    "La Coordinatrice agit par délégation de la Manager (planning) — pas de niveau hiérarchique parallèle.",
                    Set.of(), Set.of(new DroitDelegue("planning", "G")), true));
        }
    }

    record CompteInitial(String profil, String nom, String email, Set<String> perimetres) {
    }
}
