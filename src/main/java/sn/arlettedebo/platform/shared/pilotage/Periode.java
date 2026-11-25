package sn.arlettedebo.platform.shared.pilotage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Période de pilotage, bornes incluses, en jours calendaires de Dakar. */
public record Periode(LocalDate du, LocalDate au) {

    public static final int MAX_JOURS = 366;

    public Periode {
        if (du == null || au == null || au.isBefore(du)) {
            throw DomainException.invalide("Période invalide : la fin doit suivre le début.");
        }
        if (ChronoUnit.DAYS.between(du, au) + 1 > MAX_JOURS) {
            throw DomainException.invalide("Période limitée à " + MAX_JOURS + " jours.");
        }
    }

    public static Periode jour(LocalDate jour) {
        return new Periode(jour, jour);
    }

    public long jours() {
        return ChronoUnit.DAYS.between(du, au) + 1;
    }

    /** Période de même durée qui précède immédiatement (comparaison « vs période précédente »). */
    public Periode precedente() {
        return new Periode(du.minusDays(jours()), du.minusDays(1));
    }

    /** Début inclus. */
    public Instant debut() {
        return du.atStartOfDay(AuditingConfig.ZONE).toInstant();
    }

    /** Fin exclue (lendemain de {@code au} à minuit). */
    public Instant fin() {
        return au.plusDays(1).atStartOfDay(AuditingConfig.ZONE).toInstant();
    }
}
