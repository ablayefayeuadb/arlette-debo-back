package sn.arlettedebo.platform.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Base de test repartie de zéro : clean + migrate (le profil prod interdit clean). */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabaseConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
