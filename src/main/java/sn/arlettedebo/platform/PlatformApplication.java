package sn.arlettedebo.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.modulith.Modulithic;

/**
 * Plateforme Arlette Debo — monolithe modulaire.
 *
 * <p>Chaque sous-package direct est un module applicatif (vérifié par Spring Modulith) :
 * {@code iam}, {@code audit}, {@code referentiel}, {@code rh}, {@code planning}, {@code qualite},
 * {@code caisse}, {@code commercial}, {@code stock}, {@code communication}, {@code finance}.
 * Le package {@code shared} est le noyau partagé (ouvert).
 */
@Modulithic(systemName = "Arlette Debo", sharedModules = "shared")
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
