package sn.arlettedebo.platform.commercial.internal.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
import sn.arlettedebo.platform.commercial.internal.domain.Client;
import sn.arlettedebo.platform.commercial.internal.domain.ClientRepository;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisation;
import sn.arlettedebo.platform.commercial.internal.domain.DemandeAutorisationRepository;
import sn.arlettedebo.platform.shared.security.Profils;

/** Données de démonstration : fiches clientes et gestes commerciaux (deux en attente d'arbitrage). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(41)
class CommercialSeeder implements ApplicationRunner {

    private final ClientRepository clients;
    private final DemandeAutorisationRepository demandes;
    private final UserDirectory annuaire;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demandes.count() > 0) {
            return;
        }
        LocalDate j = LocalDate.now(clock);
        Client aissatou = clients.save(new Client(new Client.Donnees("SKY", "Aïssatou Ndiaye", "771112233", null, LocalDate.of(1992, j.getMonthValue(), Math.min(j.getDayOfMonth() + 2, 28)),
                Client.Statut.ABONNEE, "Instagram", true)));
        aissatou.enregistrerVisite(j.minusDays(3), null);
        Client fatou = clients.save(new Client(new Client.Donnees("SKY", "Fatou Sow", "772223344", null, null, Client.Statut.CLIENTE, "Bouche-à-oreille", false)));
        fatou.enregistrerVisite(j.minusDays(120), null);
        clients.save(new Client(new Client.Donnees("KIDS", "Maman de Awa K.", "763334455", null, null, Client.Statut.PROSPECT, "Salon", true)));

        UUID khady = compte(Profils.CAISSIER);
        UUID binetou = compte(Profils.CHARGE_CLIENTELE);
        UUID dO = compte(Profils.DO);
        var maintenant = clock.instant();
        demandes.save(new DemandeAutorisation(new DemandeAutorisation.Donnees("SKY", "COMPENSATION", aissatou.getId(), "Aïssatou Ndiaye", 4_000,
                "Attente 40 min — geste commercial", khady, maintenant.minus(Duration.ofHours(2)))));
        demandes.save(new DemandeAutorisation(new DemandeAutorisation.Donnees("KIDS", "REMISE", null, "Famille Diallo", 7_500,
                "Fratrie de 3 enfants — remise 15 %", binetou, maintenant.minus(Duration.ofHours(5)))));
        DemandeAutorisation traitee = new DemandeAutorisation(new DemandeAutorisation.Donnees("SKY", "REMISE", fatou.getId(), "Fatou Sow", 2_000,
                "Fidélité", khady, maintenant.minus(Duration.ofDays(3))));
        traitee.decider(true, dO, maintenant.minus(Duration.ofDays(3)).plus(Duration.ofHours(1)), "Accordé");
        demandes.save(traitee);
        log.info("Seeder commercial : 3 clientes, 3 demandes d'autorisation.");
    }

    private UUID compte(String profil) {
        return annuaire.actifs().stream().filter(u -> u.profil().equals(profil)).findFirst()
                .map(UserDirectory.UserRef::id).orElseThrow(() -> new IllegalStateException("Compte " + profil + " absent du seed"));
    }
}
