package sn.arlettedebo.platform.stock.internal.seed;

import java.time.Clock;
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
import sn.arlettedebo.platform.shared.config.AuditingConfig;
import sn.arlettedebo.platform.shared.security.Profils;
import sn.arlettedebo.platform.stock.internal.domain.Article;
import sn.arlettedebo.platform.stock.internal.domain.ArticleRepository;
import sn.arlettedebo.platform.stock.internal.domain.Commande;
import sn.arlettedebo.platform.stock.internal.domain.CommandeRepository;
import sn.arlettedebo.platform.stock.internal.domain.Comptage;
import sn.arlettedebo.platform.stock.internal.domain.ComptageRepository;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStock;
import sn.arlettedebo.platform.stock.internal.domain.MouvementStockRepository;

/** Données de démonstration stock : catalogue, une rupture, des alertes, un écart d'inventaire, une commande à valider. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
@Order(42)
class StockSeeder implements ApplicationRunner {

    private final ArticleRepository articles;
    private final MouvementStockRepository mouvements;
    private final ComptageRepository comptages;
    private final CommandeRepository commandes;
    private final UserDirectory annuaire;
    private final Clock clock;

    private static final String CONSOMMABLE = "CONSOMMABLE";
    private static final String PRODUIT = "PRODUIT_BOUTIQUE";
    private static final String SKY = "SKY";
    private static final String PIECE = "pièce";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (articles.count() > 0) {
            return;
        }
        UUID amy = compte(Profils.VENDEUR_BOUTIQUE);
        UUID inventaires = compte(Profils.INVENTAIRE);
        Object[][] catalogue = {
            {"Serviettes", CONSOMMABLE, "SKY", PIECE, 15, 1500L, null, 8},
            {"Capes de coupe", CONSOMMABLE, "SKY", PIECE, 6, 4000L, null, 12},
            {"Shampoing professionnel 1 L", CONSOMMABLE, "SKY", "flacon", 4, 9500L, null, 3},
            {"Gants nitrile", CONSOMMABLE, "KIDS", "boîte", 5, 3500L, null, 0},
            {"Démêlant enfant", CONSOMMABLE, "KIDS", "flacon", 4, 4500L, null, 9},
            {"Huile de karité 250 ml", PRODUIT, SKY, PIECE, 5, 3000L, 6500L, 14},
            {"Bonnet satin", PRODUIT, SKY, PIECE, 8, 2500L, 5000L, 25},
            {"Masque capillaire", PRODUIT, SKY, "pot", 5, 5500L, 12000L, 4},
        };
        var maintenant = clock.instant();
        Article serviettes = null;
        Article bonnet = null;
        Article masque = null;
        int i = 0;
        for (Object[] c : catalogue) {
            Article a = articles.save(new Article(new Article.Donnees("ART-" + String.format("%03d", ++i), (String) c[0], (String) c[1], (String) c[2],
                    (String) c[3], (Integer) c[4], (Long) c[5], (Long) c[6], "Fournisseur Dakar", null)));
            int stock = (Integer) c[7];
            if (stock > 0) {
                mouvements.save(MouvementStock.appliquer(new MouvementStock.Donnees(a, MouvementStock.Type.ENTREE, stock, "Stock initial", amy, null, maintenant, AuditingConfig.ZONE)));
            }
            if (a.getNom().equals("Serviettes")) {
                serviettes = a;
            }
            if (a.getNom().equals("Bonnet satin")) {
                bonnet = a;
            }
            if (a.getNom().equals("Masque capillaire")) {
                masque = a;
            }
        }
        comptages.save(new Comptage(serviettes, 6, inventaires, maintenant));

        Commande besoin = new Commande("CMD-" + Year.now(clock) + "-" + String.format("%05d", commandes.prochainNumero()), SKY,
                "Cosmétiques de l'Ouest", amy);
        besoin.ajouterLigne(masque, 12);
        besoin.ajouterLigne(bonnet, 20);
        commandes.save(besoin);
        log.info("Seeder stock : {} articles, 1 écart d'inventaire, 1 commande à valider.", catalogue.length);
    }

    private UUID compte(String profil) {
        return annuaire.actifs().stream().filter(u -> u.profil().equals(profil)).findFirst()
                .map(UserDirectory.UserRef::id).orElseThrow(() -> new IllegalStateException("Compte " + profil + " absent du seed"));
    }
}
