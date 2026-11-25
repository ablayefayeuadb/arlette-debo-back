package sn.arlettedebo.platform.iam.internal.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.internal.domain.Utilisateur;
import sn.arlettedebo.platform.iam.internal.domain.UtilisateurRepository;

/**
 * Bascule les e-mails {@code *@arlettedebo.sn} vers {@code *@yopmail.com} (inbox de test).
 * Idempotent ; tourne avant {@link IamSeeder}.
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
class EmailYopmailMigrator implements ApplicationRunner {

    private static final String SOURCE = "@arlettedebo.sn";
    private static final String CIBLE = "@yopmail.com";

    private final UtilisateurRepository utilisateurs;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int n = 0;
        for (Utilisateur u : utilisateurs.findAll()) {
            String email = u.getEmail();
            if (email == null || !email.endsWith(SOURCE)) {
                continue;
            }
            String local = email.substring(0, email.length() - SOURCE.length());
            if (local.isBlank()) {
                continue;
            }
            String nouveau = local + CIBLE;
            u.changerEmail(nouveau);
            utilisateurs.save(u);
            n++;
            log.info("E-mail basculé vers yopmail : {} → {}", email, nouveau);
        }
        if (n > 0) {
            log.info("Migration yopmail : {} compte(s) mis à jour.", n);
        }
    }
}
