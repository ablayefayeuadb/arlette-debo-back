package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Vendeur boutique (stocks S/G) : magasins autonomes — catalogue, ventes, stocks, commandes.
 * Caissier : coin boutique du salon (produits). Assistant inventaires : comptages.
 */
@DisplayName("Stocks & boutique : catalogue, mouvements, inventaire, commandes, vente")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockTests extends ApiTest {

    private static String article;
    /** Boutique autonome rattachée au compte vendeur (plus de SKY sur ce profil). */
    private String magasin;

    private long stock(String email, String id) throws Exception {
        JsonNode l = lire(mvc.perform(en(email, get("/api/v1/stock/articles").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        for (JsonNode a : l) {
            if (id.equals(a.path("id").asString())) {
                return a.path("stock").asLong();
            }
        }
        throw new AssertionError("article non visible");
    }

    @BeforeAll
    void ouvrirMagasin() throws Exception {
        String vendeurId = lire(mvc.perform(en(BOUTIQUE, get("/api/v1/auth/moi"))).andExpect(status().isOk()).andReturn())
                .path("id").asText();
        magasin = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")),
                        Map.of("libelle", "Boutique Stock Tests", "type", "BOUTIQUE", "responsableId", vendeurId)))
                .andExpect(status().isCreated()).andReturn()).path("code").asText();
        oublierJeton(BOUTIQUE);
    }

    @Test
    @Order(1)
    @DisplayName("Catalogue : la Boutique crée et paramètre ; inventaires et Manager ne créent pas")
    void catalogue() throws Exception {
        Map<String, Object> a = Map.of("nom", "Sérum pointes 50 ml", "categorie", "PRODUIT_BOUTIQUE", "perimetre", magasin,
                "unite", "flacon", "seuil", 3, "prixAchat", 4000, "prixVente", 9000, "fournisseur", "Cosmétiques de l'Ouest");
        JsonNode cree = lire(mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/articles")), a)).andExpect(status().isCreated()).andReturn());
        article = cree.path("id").asString();
        assertThat(cree.path("reference").asString()).matches("ART-\\d{4}");
        assertThat(cree.path("stock").asInt()).isZero();
        assertThat(cree.path("niveau").asString()).isEqualTo("RUPTURE");
        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/articles")), a)).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/stock/articles")), a)).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + article)),
                        Map.of("seuil", 2, "prixAchat", 4000, "prixVente", 9500, "actif", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prixVente").value(9500));
    }

    @Test
    @Order(1)
    @DisplayName("Catalogue : prix d'achat facultatif, prix de vente obligatoire ; filtre par type de produit")
    void prixAchatFacultatifEtFiltreType() throws Exception {
        Map<String, Object> sansAchat = Map.of("nom", "Masques Diarra", "categorie", "PRODUIT_BOUTIQUE", "perimetre", magasin,
                "unite", "pot", "seuil", 1, "prixVente", 7000, "fournisseur", "Labo test", "typeProduit", "MASQUES");
        JsonNode cree = lire(mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/articles")), sansAchat))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.prixAchat").doesNotExist()).andReturn());
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/articles")), Map.of("nom", "Sans prix vente", "categorie", "PRODUIT_BOUTIQUE",
                "perimetre", magasin, "unite", "pot", "seuil", 1, "prixAchat", 1000, "fournisseur", "Labo test")))
                .andExpect(status().isBadRequest());
        mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles")).param("typeProduit", "MASQUES").param("taille", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[?(@.id=='" + cree.path("id").asString() + "')]").exists());
        mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles")).param("typeProduit", "LAIT").param("taille", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[?(@.id=='" + cree.path("id").asString() + "')]").isEmpty());
        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + cree.path("id").asString())),
                Map.of("seuil", 1, "prixVente", 7000, "actif", false))).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("Mouvements : entrée, sortie motivée, pas de stock négatif ; ventes et ajustements hors saisie manuelle")
    void mouvements() throws Exception {
        String url = "/api/v1/stock/articles/" + article + "/mouvements";
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("type", "ENTREE", "quantite", 12, "motif", "Stock initial"))).andExpect(status().isCreated());
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("type", "CASSE", "quantite", 1))).andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("type", "CASSE", "quantite", 1, "motif", "Flacon tombé")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantite").value(-1));
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("type", "VENTE", "quantite", 1))).andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("type", "SORTIE", "quantite", 500, "motif", "Erreur")))
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFISANT"));
        mvc.perform(jsonBody(en(INVENTAIRES, post(url)), Map.of("type", "ENTREE", "quantite", 1))).andExpect(status().isForbidden());
        assertThat(stock(BOUTIQUE, article)).isEqualTo(11);
        mvc.perform(en(INVENTAIRES, get("/api/v1/stock/mouvements"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].article").exists());
    }

    @Test
    @Order(3)
    @DisplayName("Inventaire : l'assistant compte, l'écart remonte à la Boutique qui ajuste (mouvement tracé) ; un seul écart ouvert")
    void inventaire() throws Exception {
        JsonNode c = lire(mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/articles/" + article + "/comptages")),
                        Map.of("compte", 9, "commentaire", "Rayon 2"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.theorique").doesNotExist())
                .andExpect(jsonPath("$.ecart").doesNotExist()).andReturn());
        String id = c.path("id").asString();
        assertThat(c.path("statut").asString()).isEqualTo("ECART_SIGNALE");
        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/articles/" + article + "/comptages")), Map.of("compte", 8)))
                .andExpect(status().isConflict());
        JsonNode ecarts = lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/comptages").param("ecarts", "true")))
                .andExpect(status().isOk()).andReturn()).path("contenu");
        JsonNode ecart = null;
        for (JsonNode e : ecarts) {
            if (id.equals(e.path("id").asString())) {
                ecart = e;
                break;
            }
        }
        assertThat(ecart).isNotNull();
        assertThat(ecart.path("ecart").asInt()).isEqualTo(-2);
        assertThat(ecart.path("theorique").asInt()).isEqualTo(11);
        mvc.perform(en(BOUTIQUE, get("/api/v1/pilotage/a-traiter")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].type").value("TRAITER_ECART"));
        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/comptages/" + id + "/traitement")), Map.of("ajuster", true)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/comptages/" + id + "/traitement")), Map.of("ajuster", false)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/comptages/" + id + "/traitement")), Map.of("ajuster", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("AJUSTE")).andExpect(jsonPath("$.traitePar").value("Responsable boutique"));
        assertThat(stock(BOUTIQUE, article)).isEqualTo(9);
        // Article SKY sans écart déjà ouvert (le seed en laisse un ; d'autres tests peuvent en ajouter).
        JsonNode aCompter = lire(mvc.perform(en(INVENTAIRES, get("/api/v1/stock/inventaire/articles")
                        .param("perimetre", "SKY").param("taille", "50")))
                .andExpect(status().isOk()).andReturn()).path("contenu");
        String libre = null;
        for (JsonNode a : aCompter) {
            if (!a.path("ecartOuvert").asBoolean(false)) {
                libre = a.path("id").asString();
                break;
            }
        }
        assertThat(libre).as("un article SKY sans écart ouvert").isNotBlank();
        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/articles/" + libre + "/comptages")), Map.of("compte", 0)))
                .andExpect(status().isCreated());
        mvc.perform(en(INVENTAIRES, get("/api/v1/stock/inventaire/articles").param("taille", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].id").exists())
                .andExpect(jsonPath("$.contenu[0].stock").doesNotExist())
                .andExpect(jsonPath("$.contenu[0].ecartOuvert").exists());
    }

    @Test
    @Order(4)
    @DisplayName("Commande : besoin (Boutique) → validation DG → passation → réception ⇒ entrées en stock")
    void commande() throws Exception {
        JsonNode cmd = lire(mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/commandes")), Map.of("perimetre", magasin,
                        "fournisseur", "Cosmétiques de l'Ouest", "lignes", List.of(Map.of("articleId", article, "quantite", 6)))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montantEstime").value(24000)).andReturn());
        String id = cmd.path("id").asString();
        mvc.perform(en(BOUTIQUE, post("/api/v1/stock/commandes/" + id + "/passation"))).andExpect(status().isConflict());
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/commandes/" + id + "/decision")), Map.of("valider", true)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DG, post("/api/v1/stock/commandes/" + id + "/decision")), Map.of("valider", true))).andExpect(status().isOk());
        mvc.perform(en(BOUTIQUE, post("/api/v1/stock/commandes/" + id + "/passation"))).andExpect(jsonPath("$.statut").value("COMMANDEE"));
        mvc.perform(en(BOUTIQUE, post("/api/v1/stock/commandes/" + id + "/reception"))).andExpect(jsonPath("$.statut").value("RECUE"));
        mvc.perform(en(BOUTIQUE, post("/api/v1/stock/commandes/" + id + "/reception"))).andExpect(status().isConflict());
        assertThat(stock(BOUTIQUE, article)).isEqualTo(15);
        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/commandes")), Map.of("perimetre", magasin, "fournisseur", "X",
                "lignes", List.of(Map.of("articleId", article, "quantite", 1))))).andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Vente boutique autonome : facture + sortie ; paiement au caissier ; coin salon = caissier")
    void vente() throws Exception {
        mvc.perform(en(BOUTIQUE, get("/api/v1/caisse/sessions/du-jour").param("perimetre", "SKY")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/ventes")), Map.of("perimetre", magasin,
                        "lignes", List.of(Map.of("articleId", article, "quantite", 1)))))
                .andExpect(status().isBadRequest());
        JsonNode v = lire(mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/ventes")), Map.of("perimetre", magasin,
                        "clienteNom", "Aïssatou Ndiaye", "telephone", "77 123 45 67",
                        "lignes", List.of(Map.of("articleId", article, "quantite", 2)))))
                .andExpect(status().isCreated()).andReturn());
        assertThat(v.path("montant").asLong()).isEqualTo(19000);
        assertThat(v.path("clienteNom").asString()).isEqualTo("Aïssatou Ndiaye");
        assertThat(v.path("telephone").asString()).isEqualTo("771234567");
        String factureId = v.path("id").asString();
        assertThat(factureId).isNotBlank();
        assertThat(v.path("numero").asString()).startsWith("FAC-");
        assertThat(v.path("lignes").isArray()).isTrue();
        assertThat(stock(BOUTIQUE, article)).isEqualTo(13);
        mvc.perform(en(BOUTIQUE, get("/api/v1/stock/factures/" + factureId + "/pdf")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(org.springframework.http.MediaType.APPLICATION_PDF));
        JsonNode listeFactures = lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/factures").param("perimetre", magasin)))
                .andExpect(status().isOk()).andReturn());
        assertThat(listeFactures.path("contenu").toString()).contains(factureId);
        mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/ventes")),
                        Map.of("perimetre", magasin, "clienteNom", "X", "telephone", "771234567",
                                "lignes", List.of(Map.of("articleId", article, "quantite", 99)))))
                .andExpect(jsonPath("$.code").value("STOCK_INSUFFISANT"));
        assertThat(stock(BOUTIQUE, article)).isEqualTo(13);

        JsonNode session = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/caisse/sessions/du-jour").param("perimetre", "SKY")))
                .andExpect(status().isOk()).andReturn());
        JsonNode impayees = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/caisse/factures/impayees").param("perimetre", "SKY")))
                .andExpect(status().isOk()).andReturn());
        assertThat(impayees.path("contenu").toString()).contains(factureId);
        // CaisseSkyTests peut avoir déjà clôturé la session du jour dans la même JVM.
        if ("OUVERTE".equals(session.path("session").path("statut").asString())) {
            String sessionId = session.path("session").path("id").asString();
            int nbAvant = session.path("encaissements").size();
            mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + sessionId + "/factures/" + factureId + "/paiement")),
                            Map.of("modePaiement", "WAVE")))
                    .andExpect(status().isCreated());
            JsonNode apres = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/caisse/sessions/du-jour").param("perimetre", "SKY"))).andReturn());
            assertThat(apres.path("encaissements").size()).isEqualTo(nbAvant + 1);
        }

        mvc.perform(jsonBody(en(INVENTAIRES, post("/api/v1/stock/ventes")), Map.of("perimetre", magasin,
                "clienteNom", "X", "telephone", "771234567",
                "lignes", List.of(Map.of("articleId", article, "quantite", 1))))).andExpect(status().isForbidden());

        // Coin boutique salon : le caissier vend un produit SKY (pas le vendeur)
        JsonNode produitsSky = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/stock/articles")
                        .param("perimetre", "SKY").param("categorie", "PRODUIT_BOUTIQUE").param("vendables", "true").param("taille", "20")))
                .andExpect(status().isOk()).andReturn());
        assertThat(produitsSky.path("total").asLong()).isPositive();
    }

    @Test
    @Order(6)
    @DisplayName("Restrictions de la matrice : Community sans stocks ; Manager consulte sans saisir")
    void restrictions() throws Exception {
        mvc.perform(en(COMMUNITY, get("/api/v1/stock/articles").param("taille", "100"))).andExpect(status().isForbidden());
        mvc.perform(en(MANAGER, get("/api/v1/stock/articles").param("taille", "100"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/stock/articles/" + article + "/comptages")), Map.of("compte", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(99)
    @DisplayName("Paramétrage : limité aux boutiques du vendeur ; un article désactivé quitte le catalogue et peut être réactivé")
    void parametrageEtDesactivation() throws Exception {
        // Vendeur autonome ne modifie pas un consommable Kids
        String kids = lire(mvc.perform(en(INVENTAIRES, get("/api/v1/stock/articles").param("taille", "100").param("perimetre", "KIDS")))
                .andExpect(status().isOk()).andReturn()).path("contenu").get(0).path("id").asString();
        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + kids)), Map.of("seuil", 1, "actif", true)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + article)), Map.of("seuil", -1, "actif", true)))
                .andExpect(status().isBadRequest());

        Map<String, Object> a = Map.of("nom", "Huile démêlante 100 ml", "categorie", "PRODUIT_BOUTIQUE", "perimetre", magasin,
                "unite", "flacon", "seuil", 2, "prixAchat", 3000, "prixVente", 7000, "fournisseur", "Karité Sénégal");
        String id = lire(mvc.perform(jsonBody(en(BOUTIQUE, post("/api/v1/stock/articles")), a)).andExpect(status().isCreated()).andReturn())
                .path("id").asString();
        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + id)),
                        Map.of("seuil", 4, "prixAchat", 3200, "prixVente", 7500, "fournisseur", "Karité Sénégal", "actif", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false)).andExpect(jsonPath("$.fournisseur").value("Karité Sénégal"));
        assertThat(lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles").param("taille", "100"))).andReturn()).path("contenu").toString()).doesNotContain(id);
        assertThat(lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles").param("taille", "100").param("etat", "INACTIFS"))).andExpect(status().isOk()).andReturn()).path("contenu").toString())
                .contains(id);
        mvc.perform(en(INVENTAIRES, get("/api/v1/stock/articles").param("etat", "INACTIFS"))).andExpect(status().isForbidden());

        mvc.perform(jsonBody(en(BOUTIQUE, put("/api/v1/stock/articles/" + id)), Map.of("seuil", 4, "prixAchat", 3200, "prixVente", 7500, "actif", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        assertThat(lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles").param("taille", "100"))).andReturn()).path("contenu").toString()).contains(id);
    }
}
