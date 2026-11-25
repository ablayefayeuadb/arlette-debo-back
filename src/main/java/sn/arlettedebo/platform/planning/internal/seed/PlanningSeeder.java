package sn.arlettedebo.platform.planning.internal.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.planning.internal.PlanningService;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous.Source;
import sn.arlettedebo.platform.planning.internal.domain.RendezVous.Statut;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository;
import sn.arlettedebo.platform.referentiel.Referentiels;
import sn.arlettedebo.platform.referentiel.Referentiels.ParametresCreneaux;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.config.AuditingConfig;

/**
 * Données de démonstration planning : 7 jours de rendez-vous (réalisés, annulés, no-show), la journée en cours,
 * et la grille métier des créneaux sur les 4 prochaines semaines (complétée à chaque démarrage).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(44)
class PlanningSeeder implements ApplicationRunner {

    private static final String[] CLIENTES = {"Aïssatou N.", "Mame D.", "Coumba S.", "Ndèye F.", "Rokhaya B.", "Astou G."};
    private static final String[][] JOURNEE = {{"09:30", "BRUSHING"}, {"10:30", "NAPPY"}, {"12:30", "DEFRISAGE"}, {"14:00", "RITUEL_SOIN"}, {"15:30", "BRUSHING"}};

    private final RendezVousRepository rdvs;
    private final PlanningService planning;
    private final Effectifs effectifs;
    private final Referentiels referentiels;
    private final Clock clock;

    /** Désactivé en test : les scénarios ouvrent eux-mêmes leurs créneaux. */
    @org.springframework.beans.factory.annotation.Value("${app.seed.creneaux-en-ligne:true}")
    private boolean creneauxEnLigne;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (creneauxEnLigne) {
            ouvrirCreneaux();
        }
        if (rdvs.count() > 0) {
            return;
        }
        UUID awa = effectifs.coiffeuses("SKY").stream().findFirst().map(Effectifs.EmployeRef::id).orElse(null);
        LocalDate aujourdhui = LocalDate.now(clock);
        int n = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate jour = aujourdhui.minusDays(i);
            for (int k = 0; k < JOURNEE.length; k++) {
                var prestation = referentiels.prestation(JOURNEE[k][1]).orElseThrow();
                RendezVous r = new RendezVous(new RendezVous.Donnees("SKY", jour, LocalTime.parse(JOURNEE[k][0]), prestation.dureeMin(), null,
                        CLIENTES[(i + k) % CLIENTES.length], "77 000 00 0" + k, prestation.code(), awa, prestation.prix(),
                        k % 3 == 0 ? Source.EN_LIGNE : Source.ACCUEIL));
                if (i > 0) {
                    derouler(r, jour, i + k, (i * 7L + k * 3L) % 25, prestation.dureeMin());
                }
                rdvs.save(r);
                n++;
            }
            RendezVous kids = new RendezVous(new RendezVous.Donnees("KIDS", jour, LocalTime.of(10, 0), 90, null, "Enfant " + (i + 1), null, "KIDS_TRESSES", null, 12_000,
                    Source.TELEPHONE));
            if (i > 0) {
                derouler(kids, jour, 1, 10, 90);
            }
            rdvs.save(kids);
            n++;
        }
        log.info("Seeder planning : {} rendez-vous.", n);
    }

    /**
     * Grille métier (Sky : lun–jeu/sam–dim 10h & 15h, ven 9h30 & 14h30 ; Kids : mer 15h, ven–dim 10h/12h/15h)
     * sur les 4 prochaines semaines. Idempotent : complète sans toucher aux créneaux existants.
     */
    private void ouvrirCreneaux() {
        LocalDate aujourdhui = LocalDate.now(clock);
        LocalDate fin = aujourdhui.plusWeeks(4).minusDays(1);
        int n = 0;
        for (String salon : List.of("SKY", "KIDS")) {
            int capacite = referentiels.parametresCreneaux(salon).map(ParametresCreneaux::capacite).orElse(1);
            n += planning.genererGrilleMetier(salon, aujourdhui, fin, Math.max(1, capacite));
        }
        if (n > 0) {
            log.info("Seeder planning : {} créneaux de la grille métier (4 semaines).", n);
        }
    }

    /** Fait vivre un rendez-vous passé : annulation, absence ou prestation réalisée (avec retard de prise en charge). */
    private static void derouler(RendezVous r, LocalDate jour, int graine, long attenteMin, int dureeMin) {
        var debut = ZonedDateTime.of(jour, r.getHeure(), AuditingConfig.ZONE).toInstant();
        if (graine % 9 == 0) {
            r.changerStatut(Statut.ANNULEE, debut.minus(Duration.ofHours(4)));
        } else if (graine % 11 == 0) {
            r.changerStatut(Statut.ABSENTE, debut.plus(Duration.ofMinutes(30)));
        } else {
            r.changerStatut(Statut.ARRIVEE, debut.minus(Duration.ofMinutes(5)));
            r.changerStatut(Statut.EN_COURS, debut.plus(Duration.ofMinutes(attenteMin)));
            r.changerStatut(Statut.TERMINEE, debut.plus(Duration.ofMinutes(attenteMin + dureeMin)));
        }
    }
}
