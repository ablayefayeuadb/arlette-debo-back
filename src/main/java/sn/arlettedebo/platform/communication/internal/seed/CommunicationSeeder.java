package sn.arlettedebo.platform.communication.internal.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ancien seed du calendrier éditorial — désactivé : le module Communication
 * (ex-périmètre Community manager) n'est plus exposé à aucun profil.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(43)
class CommunicationSeeder implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seeder communication : ignoré (module retiré).");
    }
}
