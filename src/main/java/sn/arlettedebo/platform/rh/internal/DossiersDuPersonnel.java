package sn.arlettedebo.platform.rh.internal;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.arlettedebo.platform.iam.CompteCree;
import sn.arlettedebo.platform.iam.UserDirectory;
import sn.arlettedebo.platform.shared.security.Profils;

/**
 * Tout utilisateur du système pointe, il lui faut donc un dossier du personnel :
 * <ul>
 *   <li>création d'un compte : dossier minimal créé dans la même transaction ;</li>
 *   <li>au démarrage : rattrapage des comptes actifs qui n'en ont pas (comptes antérieurs à cette règle).</li>
 * </ul>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
class DossiersDuPersonnel implements ApplicationRunner {

    private final RhService rh;
    private final UserDirectory annuaire;

    @EventListener
    void surCreationDeCompte(CompteCree e) {
        annuaire.parId(e.utilisateurId()).ifPresent(u -> {
            if (Profils.ABONNEE.equals(u.profil())) {
                return; // portail cliente : pas de dossier RH ni de pointage
            }
            rh.creerDossierAutomatique(u, poste(u, annuaire.libellesProfils()));
        });
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, String> libelles = annuaire.libellesProfils();
        long crees = annuaire.actifs().stream()
                .filter(u -> !Profils.ABONNEE.equals(u.profil()))
                .filter(u -> rh.creerDossierAutomatique(u, poste(u, libelles)).isPresent())
                .count();
        if (crees > 0) {
            log.info("Dossiers du personnel : {} dossier(s) créé(s) pour des comptes qui n'en avaient pas.", crees);
        }
    }

    private static String poste(UserDirectory.UserRef u, Map<String, String> libelles) {
        String libelle = libelles.getOrDefault(u.profil(), u.profil());
        // Profil COIFFEUR : poste unifié « Coiffeuse » (évite « Coiffeur / Coiffeuse » du libellé historique).
        if (Profils.COIFFEUR.equals(u.profil())) {
            return "Coiffeuse";
        }
        return libelle;
    }
}
