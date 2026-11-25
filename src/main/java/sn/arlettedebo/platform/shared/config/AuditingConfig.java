package sn.arlettedebo.platform.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Horloge unique de l'application (fuseau Africa/Dakar) et auteur des modifications JPA.
 * L'auteur est l'identifiant du sujet JWT (UUID de l'utilisateur).
 */
@Configuration(proxyBeanMethods = false)
public class AuditingConfig {

    public static final ZoneId ZONE = ZoneId.of("Africa/Dakar");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    @Bean
    AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(auth.getName()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        };
    }
}
