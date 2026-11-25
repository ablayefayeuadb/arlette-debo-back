package sn.arlettedebo.platform.rh.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Contrôle des départs non pointés, tous les quarts d'heure : il ne signale une journée qu'une fois passée l'heure
 * de contrôle fixée par la DG (paramètre {@code pointage.controle-depart}, 23 h 00 par défaut), ou le lendemain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PointageControleJob {

    private final PointageService pointage;

    @Scheduled(cron = "0 */15 * * * *", zone = "Africa/Dakar")
    void controler() {
        int n = pointage.signalerDepartsNonPointes();
        if (n > 0) {
            log.info("{} départ(s) non pointé(s) signalé(s).", n);
        }
    }
}
