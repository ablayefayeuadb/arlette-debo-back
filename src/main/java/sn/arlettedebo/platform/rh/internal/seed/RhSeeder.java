package sn.arlettedebo.platform.rh.internal.seed;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.iam.UserDirectory.UserRef;
import sn.arlettedebo.platform.rh.internal.domain.Employe;
import sn.arlettedebo.platform.rh.internal.domain.EmployeRepository;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Seeder RH — idempotent : un dossier du personnel lié à chaque compte opérationnel
 * (la Direction n'a pas de dossier de pointage). S'exécute après le seeder IAM.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
class RhSeeder implements ApplicationRunner {

    /** Poste, salaire de base et compétences par profil (données de démonstration). */
    private static final Map<String, Fiche> FICHES = Map.of(
            Profils.MANAGER, new Fiche("Manager des Salons", 350_000, Map.of()),
            Profils.COORDINATION, new Fiche("Coordinatrice Opérationnelle", 220_000, Map.of()),
            Profils.CAISSIER, new Fiche("Responsable Caisse & Commerciale", 180_000, Map.of()),
            Profils.CHARGE_CLIENTELE, new Fiche("Chargée de Clientèle & Commerciale", 160_000, Map.of()),
            Profils.COIFFEUR, new Fiche("Coiffeuse", 150_000, Map.of(
                    "BRUSHING", 3, "NAPPY", 2, "DEFRISAGE", 2, "TRESSES", 2, "LOCKS", 2,
                    "RITUEL_SOIN", 2, "COUPE", 2, "SHAMPOOING_SOIN", 2)),
            Profils.VENDEUR_BOUTIQUE, new Fiche("Responsable Boutique", 170_000, Map.of()),
            Profils.INVENTAIRE, new Fiche("Assistant Inventaires & Stocks", 140_000, Map.of()),
            Profils.COMMUNITY_MANAGER, new Fiche("Community Manager & Content Creator", 200_000, Map.of()),
            Profils.ADMINISTRATION, new Fiche("Responsable Administration, Contrôle & Coordination", 300_000, Map.of()));

    private final UserDirectory annuaire;
    private final EmployeRepository employes;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int crees = 0;
        for (UserRef u : annuaire.actifs()) {
            Fiche f = FICHES.get(u.profil());
            if (f == null || employes.existsByUtilisateurId(u.id())) {
                continue;
            }
            String perimetre = u.perimetres().size() == 1 ? u.perimetres().iterator().next() : null;
            String matricule = "AD-" + Year.now(clock) + "-" + String.format("%04d", employes.prochainNumero());
            Employe e = new Employe(matricule, new Employe.Dossier(u.nom(), f.poste(), null, perimetre, null,
                    LocalDate.now(clock).minusMonths(18), Employe.Contrat.CDI, f.salaire(), LocalTime.of(9, 30), null, u.id()));
            f.competences().forEach((prestation, niveau) -> e.definirCompetence(prestation, niveau.shortValue(), null, LocalDate.now(clock)));
            employes.save(e);
            crees++;
        }
        log.info("Seeder RH : {} dossier(s) du personnel créé(s).", crees);
    }

    private record Fiche(String poste, long salaire, Map<String, Integer> competences) {
    }
}
