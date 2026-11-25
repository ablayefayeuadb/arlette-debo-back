package sn.arlettedebo.platform.caisse.internal.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisse;
import sn.arlettedebo.platform.caisse.internal.domain.SessionCaisseRepository;
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Données de démonstration caisse : 7 jours d'historique par périmètre.
 * J-2 Sky : écart de 2 000 FCFA (anomalie) ; J-1 : clôtures non contrôlées (à contrôler) ; J : caisses ouvertes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(40)
class CaisseSeeder implements ApplicationRunner {

    private static final String[] MODES = {"ESPECES", "WAVE", "ORANGE_MONEY", "CARTE"};

    private static final String SKY = "SKY";
    private static final long FOND = 20_000;

    private final SessionCaisseRepository sessions;
    private final UserDirectory annuaire;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sessions.count() > 0) {
            return;
        }
        Map<String, UUID> comptes = annuaire.actifs().stream()
                .collect(Collectors.toMap(UserDirectory.UserRef::profil, UserDirectory.UserRef::id, (a, b) -> a));
        UUID manager = compte(comptes, Profils.MANAGER);
        String[][] salons = {{SKY, Profils.CAISSIER}, {"KIDS", Profils.CHARGE_CLIENTELE}};
        LocalDate aujourdhui = LocalDate.now(clock);
        int n = 0;
        for (int i = 6; i >= 0; i--) {
            for (int s = 0; s < salons.length; s++) {
                sessions.save(journee(salons[s][0], compte(comptes, salons[s][1]), manager, aujourdhui.minusDays(i), i, s));
                n++;
            }
        }
        log.info("Seeder caisse : {} session(s) sur 7 jours.", n);
    }

    /** Une journée de caisse : encaissements, clôture (sauf aujourd'hui) et contrôle (sauf hier, laissé « à contrôler »). */
    private static SessionCaisse journee(String perimetre, UUID caissier, UUID manager, LocalDate jour, int anciennete, int rang) {
        SessionCaisse session = new SessionCaisse(perimetre, jour, FOND, caissier, a(jour, 9, 0));
        int nb = volume(perimetre) + (anciennete + rang) % 3;
        for (int k = 0; k < nb; k++) {
            session.encaisser("Prestation", montant(perimetre, k), MODES[(k + anciennete) % 4], caissier, a(jour, 10 + k % 8, k % 2 * 30));
        }
        if (anciennete > 0) {
            long ecart = anciennete == 2 && SKY.equals(perimetre) ? -2_000 : 0;
            session.cloturer(FOND + session.totalEspeces() + ecart, caissier, a(jour, 19, 30));
            if (anciennete > 1) {
                session.controler(manager, a(jour.plusDays(1), 9, 15), ecart == 0, ecart == 0 ? null : "Écart signalé à la DO");
            }
        }
        return session;
    }

    private static int volume(String perimetre) {
        return switch (perimetre) {
            case SKY -> 9;
            case "KIDS" -> 5;
            default -> 4;
        };
    }

    private static long montant(String perimetre, int k) {
        return switch (perimetre) {
            case SKY -> 8_000 + (k * 3_500L) % 22_000;
            case "KIDS" -> 5_000 + (k * 1_500L) % 7_000;
            default -> 3_500 + (k * 2_500L) % 9_000;
        };
    }

    private static java.time.Instant a(LocalDate jour, int h, int m) {
        return ZonedDateTime.of(jour, LocalTime.of(h, m), AuditingConfig.ZONE).toInstant();
    }

    private static UUID compte(Map<String, UUID> comptes, String profil) {
        UUID id = comptes.get(profil);
        if (id == null) {
            throw new IllegalStateException("Compte " + profil + " absent du seed");
        }
        return id;
    }
}
