package sn.arlettedebo.platform.finance.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.finance.internal.domain.Depense;
import sn.arlettedebo.platform.finance.internal.domain.DepenseRepository;
import sn.arlettedebo.platform.shared.security.Profils;

/** Données de démonstration finance : dossiers à chaque étape du circuit, dont deux à valider par la Direction. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(46)
class DepenseSeeder implements ApplicationRunner {

    private final DepenseRepository depenses;
    private final UserDirectory annuaire;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (depenses.count() > 0) {
            return;
        }
        UUID khadija = compte(Profils.ADMINISTRATION);
        UUID dg = compte(Profils.DG);
        UUID dO = compte(Profils.DO);
        var t = clock.instant();
        depenses.save(nouvelle("Réparation climatisation Sky", "MAINTENANCE", "SKY", 85_000, "Froid Service", khadija, t.minus(Duration.ofDays(1))));
        depenses.save(nouvelle("Fauteuils de coiffage (x2)", "MAINTENANCE", "SKY", 340_000, "Mobilier Pro", khadija, t.minus(Duration.ofHours(20))));
        Depense validee = nouvelle("Facture électricité", "LOYER_CHARGES", null, 212_000, "Senelec", khadija, t.minus(Duration.ofDays(4)));
        validee.valider(dg, t.minus(Duration.ofDays(3)));
        depenses.save(validee);
        Depense payee = nouvelle("Boost Instagram Rituel Soin", "MARKETING", "SKY", 30_000, "Meta", khadija, t.minus(Duration.ofDays(12)));
        payee.valider(dO, t.minus(Duration.ofDays(11)));
        payee.payer(khadija, t.minus(Duration.ofDays(10)), "WAVE");
        depenses.save(payee);
        log.info("Seeder finance : 4 dépenses.");
    }

    private Depense nouvelle(String objet, String cat, String perimetre, long montant, String fournisseur, UUID par, java.time.Instant le) {
        return new Depense(new Depense.Donnees("DEP-" + Year.now(clock) + "-" + String.format("%06d", depenses.prochainNumero()), objet, cat, perimetre, montant,
                fournisseur, par, le));
    }

    private UUID compte(String profil) {
        return annuaire.actifs().stream().filter(u -> u.profil().equals(profil)).findFirst()
                .map(UserDirectory.UserRef::id).orElseThrow(() -> new IllegalStateException("Compte " + profil + " absent du seed"));
    }
}
