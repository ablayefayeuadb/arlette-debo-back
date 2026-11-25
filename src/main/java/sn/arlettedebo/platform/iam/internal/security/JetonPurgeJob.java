package sn.arlettedebo.platform.iam.internal.security;

import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissementRepository;

/** Purge nocturne des jetons de rafraîchissement expirés depuis plus de 7 jours. */
@Slf4j
@Component
@RequiredArgsConstructor
class JetonPurgeJob {

    private final JetonRafraichissementRepository jetons;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Dakar")
    @Transactional
    public void purger() {
        int n = jetons.purgerExpires(clock.instant().minus(Duration.ofDays(7)));
        if (n > 0) {
            log.info("{} jeton(s) de rafraîchissement expiré(s) purgé(s).", n);
        }
    }
}
