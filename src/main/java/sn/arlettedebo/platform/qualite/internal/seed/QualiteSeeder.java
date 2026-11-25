package sn.arlettedebo.platform.qualite.internal.seed;

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
import sn.arlettedebo.platform.qualite.internal.domain.AvisClient;
import sn.arlettedebo.platform.qualite.internal.domain.AvisClientRepository;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestation;
import sn.arlettedebo.platform.qualite.internal.domain.ControlePrestationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformite;
import sn.arlettedebo.platform.qualite.internal.domain.NonConformiteRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Reclamation;
import sn.arlettedebo.platform.qualite.internal.domain.ReclamationRepository;
import sn.arlettedebo.platform.qualite.internal.domain.Signalement;
import sn.arlettedebo.platform.qualite.internal.domain.SignalementRepository;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenance;
import sn.arlettedebo.platform.qualite.internal.domain.TicketMaintenanceRepository;
import sn.arlettedebo.platform.rh.Effectifs;
import sn.arlettedebo.platform.shared.security.Profils;

/** Données de démonstration qualité : avis, contrôles, une NC avec plan d'action, réclamations, signalement, maintenance. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(45)
class QualiteSeeder implements ApplicationRunner {

    private final AvisClientRepository avis;
    private final ControlePrestationRepository controles;
    private final NonConformiteRepository ncs;
    private final ReclamationRepository reclamations;
    private final SignalementRepository signalements;
    private final TicketMaintenanceRepository tickets;
    private final Effectifs effectifs;
    private final UserDirectory annuaire;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (avis.count() > 0) {
            return;
        }
        var t = clock.instant();
        LocalDate j = LocalDate.now(clock);
        UUID manager = compte(Profils.MANAGER);
        UUID coordinatrice = compte(Profils.COORDINATION);
        UUID awaCompte = compte(Profils.COIFFEUR);
        UUID awa = effectifs.coiffeuses("SKY").stream().findFirst().map(Effectifs.EmployeRef::id).orElse(null);

        int[] notes = {5, 4, 5, 3, 4, 5, 4};
        for (int i = 0; i < notes.length; i++) {
            avis.save(new AvisClient(new AvisClient.Donnees(i % 3 == 2 ? "KIDS" : "SKY", t.minus(Duration.ofDays(i)), null,
                    "Cliente " + (i + 1), null, notes[i], notes[i] >= 4 ? "Très satisfaite" : "Attente un peu longue", manager)));
        }
        controles.save(new ControlePrestation(new ControlePrestation.Donnees("SKY", t.minus(Duration.ofDays(1)), null, "BRUSHING", awa, coordinatrice, 5, 4, 5, 4, "RAS", true)));
        ControlePrestation nonConforme = controles.save(new ControlePrestation(new ControlePrestation.Donnees("SKY", t.minus(Duration.ofDays(2)), null, "NAPPY", awa,
                coordinatrice, 4, 3, 2, 3, "Poste non désinfecté entre deux clientes", true)));

        NonConformite nc = new NonConformite("SKY", j.minusDays(2), NonConformite.Origine.CONTROLE, "Désinfection des outils non réalisée",
                NonConformite.Gravite.MAJEURE, nonConforme.getId(), coordinatrice);
        nc.planifier("Rappel du protocole hygiène à l'équipe", manager, j.plusDays(2));
        ncs.save(nc);

        reclamations.save(new Reclamation(new Reclamation.Donnees("SKY", t.minus(Duration.ofDays(1)), null, "Aïssatou Ndiaye", null, Reclamation.Canal.ACCUEIL,
                "Attente de 40 minutes malgré le rendez-vous", NonConformite.Gravite.MINEURE, "Geste commercial proposé")));
        Reclamation resolue = new Reclamation(new Reclamation.Donnees("KIDS", t.minus(Duration.ofDays(5)), null, "Famille Diallo", null, Reclamation.Canal.WHATSAPP,
                "Coiffure défaite le lendemain", NonConformite.Gravite.MINEURE, null));
        resolue.resoudre(manager, "Retouche offerte", t.minus(Duration.ofDays(4)));
        reclamations.save(resolue);

        signalements.save(new Signalement("SKY", t.minus(Duration.ofHours(6)), awaCompte, "BESOIN_MATERIEL", "Sèche-cheveux du poste 3 en panne"));
        tickets.save(new TicketMaintenance("SKY", "Climatisation", "Zone shampoing", "Climatisation insuffisante",
                TicketMaintenance.Urgence.HAUTE, manager));
        log.info("Seeder qualité : avis, contrôles, 1 NC, réclamations, signalement, maintenance.");
    }

    private UUID compte(String profil) {
        return annuaire.actifs().stream().filter(u -> u.profil().equals(profil)).findFirst()
                .map(UserDirectory.UserRef::id).orElseThrow(() -> new IllegalStateException("Compte " + profil + " absent du seed"));
    }
}
