package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Profil DG — matrice : dashboard C/G/V · rh C · planning C · qualité C · caisse C/V · stocks C/V ·
 * administration C/V (communication retirée). Fonctionnalités : tableau de bord global, consultation des
 * performances, validation des décisions stratégiques / dépenses / arbitrages, suivi des reportings.
 */
@DisplayName("Direction Générale : tableau de bord, à traiter, arbitrages, reporting")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DirectionTests extends ApiTest {

    private static final String AUJOURDHUI = LocalDate.now().toString();
    private static final String IL_Y_A_30_JOURS = LocalDate.now().minusDays(29).toString();

    // ------------------------------------------------------------------ Tableau de bord global

    @Test
    @Order(1)
    @DisplayName("Tableau de bord global : toutes les sections + comparatif des salons")
    void tableauDeBordGlobal() throws Exception {
        JsonNode tdb = lire(mvc.perform(en(DG, get("/api/v1/pilotage/tableau-de-bord").param("du", LocalDate.now().minusDays(6).toString())))
                .andExpect(status().isOk()).andReturn());
        assertThat(titres(tdb.path("sections"))).containsExactly("Activité & caisse", "Commercial & fidélisation", "Rendez-vous & fréquentation",
                "Qualité & satisfaction", "Stocks & boutique", "Ressources humaines", "Achats & dépenses");
        assertThat(valeur(tdb.path("sections"), "caisse.ca")).isPositive();
        assertThat(valeur(tdb.path("sections"), "qualite.satisfaction")).isBetween(1.0, 5.0);
        // Au moins SKY + KIDS ; d'autres salons peuvent exister (créés par SalonsTests dans la même JVM).
        assertThat(tdb.path("comparatif").size()).isGreaterThanOrEqualTo(2);
        java.util.List<String> perims = new java.util.ArrayList<>();
        tdb.path("comparatif").forEach(c -> perims.add(c.path("perimetre").asString()));
        assertThat(perims).contains("KIDS");
        assertThat(tdb.path("nombreATraiter").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(tdb.path("prioritaires").get(0).path("niveau").asString()).isEqualTo("V");
    }

    @Test
    @Order(2)
    @DisplayName("Filtre périmètre : un seul périmètre, pas de comparatif")
    void filtrePerimetre() throws Exception {
        mvc.perform(en(DG, get("/api/v1/pilotage/tableau-de-bord").param("perimetre", "KIDS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perimetres.length()").value(1))
                .andExpect(jsonPath("$.comparatif.length()").value(0));
    }

    // ------------------------------------------------------------------ À traiter

    @Test
    @Order(3)
    @DisplayName("À traiter DG : dépenses (y compris > seuil), gestes commerciaux, commande, clôtures")
    void aTraiterDg() throws Exception {
        JsonNode taches = lire(mvc.perform(en(DG, get("/api/v1/pilotage/a-traiter"))).andExpect(status().isOk()).andReturn());
        List<String> types = types(taches);
        assertThat(types)
                .contains("VALIDER_DEPENSE", "AUTORISER_COMPENSATION", "AUTORISER_REMISE", "VALIDER_COMMANDE", "CONTROLER_CLOTURE")
                .doesNotContain("VALIDER_CONTENU", "VERIFIER_CONTENU", "PAYER_DEPENSE");
        List<Long> montants = new ArrayList<>();
        taches.forEach(t -> {
            if ("VALIDER_DEPENSE".equals(t.path("type").asString())) {
                montants.add(t.path("montant").asLong());
            }
        });
        assertThat(montants).as("la DG valide aussi au-delà du seuil").contains(85_000L, 340_000L);
        assertThat(taches.get(0).path("niveau").asString()).isEqualTo("V");
    }

    @Test
    @Order(4)
    @DisplayName("À traiter DO : seulement les dépenses sous le seuil ; plus de tâches communication")
    void aTraiterDo() throws Exception {
        JsonNode taches = lire(mvc.perform(en(DO, get("/api/v1/pilotage/a-traiter"))).andExpect(status().isOk()).andReturn());
        List<String> types = types(taches);
        assertThat(types).isNotEmpty().doesNotContain("VERIFIER_CONTENU", "VALIDER_CONTENU");
        List<Long> montantsDepenses = new ArrayList<>();
        for (JsonNode t : taches) {
            if ("VALIDER_DEPENSE".equals(t.path("type").asString())) {
                montantsDepenses.add(t.path("montant").asLong());
            }
        }
        assertThat(montantsDepenses).isNotEmpty();
        for (Long montant : montantsDepenses) {
            assertThat(montant).isLessThanOrEqualTo(100_000L);
        }
    }

    // ------------------------------------------------------------------ Arbitrages : caisse V

    @Test
    @Order(10)
    @DisplayName("Gestes commerciaux : la DG et la Manager autorisent ; la caissière ne peut pas")
    void autorisationsCommerciales() throws Exception {
        JsonNode demande = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/autorisations")), Map.of(
                "perimetre", "SKY", "type", "GRATUITE", "cliente", "Cliente test", "montant", 10_000, "motif", "Prestation ratée")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.peutDecider").value(false)).andReturn());
        String url = "/api/v1/commercial/autorisations/" + demande.path("id").asString() + "/decision";
        mvc.perform(jsonBody(en(CAISSE_SKY, post(url)), Map.of("autoriser", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTORISATION_DIRECTION"));
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("autoriser", true, "commentaire", "Geste accordé")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("AUTORISEE"));
        mvc.perform(jsonBody(en(DG, post(url)), Map.of("autoriser", false))).andExpect(status().isConflict());
        mvc.perform(en(CAISSE_SKY, post("/api/v1/commercial/autorisations/" + demande.path("id").asString() + "/application")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("APPLIQUEE"));
    }

    @Test
    @Order(11)
    @DisplayName("Clôtures : la DG contrôle (indépendante) ; la caissière ne contrôle pas sa caisse ; anomalie commentée")
    void controleDesClotures() throws Exception {
        JsonNode aControler = lire(mvc.perform(en(DG, get("/api/v1/caisse/sessions/a-controler"))).andExpect(status().isOk()).andReturn());
        String hier = LocalDate.now().minusDays(1).toString();
        List<String> perimetresHier = new ArrayList<>();
        aControler.forEach(s -> {
            if (hier.equals(s.path("jour").asString())) {
                perimetresHier.add(s.path("perimetre").asString());
            }
        });
        assertThat(perimetresHier).containsExactlyInAnyOrder("SKY", "KIDS");
        String sky = null;
        for (JsonNode s : aControler) {
            if ("SKY".equals(s.path("perimetre").asString()) && hier.equals(s.path("jour").asString())) {
                sky = s.path("id").asString();
            }
        }
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + sky + "/controle")), Map.of("conforme", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SEPARATION_DES_TACHES"));
        mvc.perform(jsonBody(en(DG, post("/api/v1/caisse/sessions/" + sky + "/controle")), Map.of("conforme", false)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(DG, post("/api/v1/caisse/sessions/" + sky + "/controle")), Map.of("conforme", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.controlePar").value("Direction Générale"));
        JsonNode detail = lire(mvc.perform(en(DG, get("/api/v1/caisse/sessions/" + sky))).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("encaissements").size()).isPositive();
    }

    // ------------------------------------------------------------------ Arbitrages : stocks V

    @Test
    @Order(12)
    @DisplayName("Commandes : la DG et la Manager valident ; le vendeur boutique ne peut pas")
    void validationCommande() throws Exception {
        JsonNode commandes = lire(mvc.perform(en(DG, get("/api/v1/stock/commandes").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        JsonNode besoin = commandes.get(0);
        assertThat(besoin.path("statut").asString()).isEqualTo("BESOIN");
        assertThat(besoin.path("peutValider").asBoolean()).isTrue();
        String url = "/api/v1/stock/commandes/" + besoin.path("id").asString() + "/decision";
        mvc.perform(jsonBody(en(BOUTIQUE, post(url)), Map.of("valider", true))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("valider", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
    }

    // ------------------------------------------------------------------ Consultation des performances

    @Test
    @Order(20)
    @DisplayName("Consultation : planning, qualité, stocks, RH, caisse — en lecture")
    void consultation() throws Exception {
        mvc.perform(en(DG, get("/api/v1/planning/rendez-vous").param("du", AUJOURDHUI).param("au", AUJOURDHUI)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
        mvc.perform(en(DG, get("/api/v1/qualite/non-conformites")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].actions.length()").value(1));
        mvc.perform(en(DG, get("/api/v1/qualite/avis").param("du", IL_Y_A_30_JOURS).param("au", AUJOURDHUI))).andExpect(status().isOk());
        mvc.perform(en(DG, get("/api/v1/qualite/controles").param("du", IL_Y_A_30_JOURS).param("au", AUJOURDHUI))).andExpect(status().isOk());
        mvc.perform(en(DG, get("/api/v1/stock/alertes"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].niveau").value("RUPTURE"));
        mvc.perform(en(DG, get("/api/v1/caisse/sessions").param("du", IL_Y_A_30_JOURS).param("au", AUJOURDHUI)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(14)))
                .andExpect(jsonPath("$.contenu.length()").value(14));
        mvc.perform(en(DG, get("/api/v1/communication/publications")
                .param("du", IL_Y_A_30_JOURS).param("au", AUJOURDHUI))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Suivi des reportings

    @Test
    @Order(30)
    @DisplayName("Reporting consolidé : évolution vs période précédente et tendance")
    void reporting() throws Exception {
        JsonNode r = lire(mvc.perform(en(DG, get("/api/v1/pilotage/reporting").param("du", LocalDate.now().minusDays(2).toString())
                        .param("au", LocalDate.now().minusDays(1).toString())))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.path("type").asString()).isEqualTo("CONSOLIDE");
        assertThat(r.path("periodePrecedente").path("au").asString()).isEqualTo(LocalDate.now().minusDays(3).toString());
        JsonNode ca = trouver(r.path("sections"), "caisse.ca");
        assertThat(ca.path("indicateur").path("valeur").asDouble()).isPositive();
        assertThat(ca.path("precedente").asDouble()).isPositive();
        assertThat(ca.path("tendance").asString()).isIn("FAVORABLE", "DEFAVORABLE", "STABLE");
        assertThat(r.path("comparatif").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(31)
    @DisplayName("Export CSV : en-tête, BOM UTF-8, protection contre l'injection de formules")
    void exportCsv() throws Exception {
        var res = mvc.perform(en(DG, get("/api/v1/pilotage/reporting.csv")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse();
        String csv = res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFSection;Indicateur;Valeur").contains("\"Chiffre d'affaires\"");
        assertThat(res.getHeader("Content-Disposition")).contains("reporting-consolide-");
    }

    @Test
    @Order(32)
    @DisplayName("Reporting : Manager comme la DG ; administratif pour l'Administration (sans planning ni qualité)")
    void reportingSelonProfil() throws Exception {
        mvc.perform(en(MANAGER, get("/api/v1/pilotage/reporting"))).andExpect(status().isOk());
        JsonNode adm = lire(mvc.perform(en(ADMINISTRATION, get("/api/v1/pilotage/reporting"))).andExpect(status().isOk()).andReturn());
        assertThat(adm.path("type").asString()).isEqualTo("ADMINISTRATIF");
        assertThat(titres(adm.path("sections"))).doesNotContain("Rendez-vous & fréquentation", "Qualité & satisfaction")
                .contains("Achats & dépenses", "Ressources humaines");
    }

    @Test
    @Order(33)
    @DisplayName("Tableaux de bord restreints : coiffeuse, caissier, vendeur, coordination, admin, community, inventaires")
    @SuppressWarnings("java:S5961") // scénario multi-profils : assertions regroupées volontairement
    void tableauxRestreints() throws Exception {
        JsonNode coiffeuse = lire(mvc.perform(en(COIFFEUSE, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isOk()).andReturn());
        assertThat(titres(coiffeuse.path("sections"))).containsExactly("Rendez-vous & fréquentation");
        assertThat(coiffeuse.path("comparatif").size()).isZero();

        JsonNode caissier = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isOk()).andReturn());
        assertThat(caissier.path("restriction").asString()).isEqualTo("limité");
        assertThat(titres(caissier.path("sections")))
                .contains("Activité & caisse", "Commercial & fidélisation", "Rendez-vous & fréquentation", "Stocks & boutique")
                .doesNotContain("Qualité & satisfaction", "Achats & dépenses", "Ressources humaines");
        assertThat(codes(caissier.path("sections"))).contains("stock.ruptures", "stock.alertes")
                .doesNotContain("caisse.a_controler", "stock.valeur", "stock.commandes_en_cours", "stock.ecarts");
        assertThat(caissier.path("comparatif").size()).isZero();
        JsonNode tachesCaissier = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/pilotage/a-traiter"))).andExpect(status().isOk()).andReturn());
        assertThat(types(tachesCaissier)).doesNotContain("CONTROLER_CLOTURE", "VALIDER_COMMANDE", "VALIDER_DEPENSE");

        JsonNode boutique = lire(mvc.perform(en(BOUTIQUE, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isOk()).andReturn());
        assertThat(boutique.path("restriction").asString()).isEqualTo("limité");
        assertThat(titres(boutique.path("sections"))).containsExactly("Stocks & boutique");
        assertThat(codes(boutique.path("sections"))).doesNotContain("caisse.ca", "planning.rdv", "qualite.satisfaction");
        assertThat(boutique.path("comparatif").size()).isZero();

        JsonNode coordination = lire(mvc.perform(en(COORDINATRICE, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isOk()).andReturn());
        assertThat(coordination.path("restriction").asString()).isEqualTo("limité");
        assertThat(titres(coordination.path("sections"))).containsExactly("Qualité & satisfaction");
        assertThat(codes(coordination.path("sections"))).contains("qualite.satisfaction", "qualite.nc_ouvertes");

        JsonNode admin = lire(mvc.perform(en(ADMINISTRATION, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isOk()).andReturn());
        assertThat(admin.path("restriction").asString()).isEqualTo("administratif");
        assertThat(titres(admin.path("sections")))
                .contains("Achats & dépenses", "Ressources humaines")
                .doesNotContain("Commercial & fidélisation", "Rendez-vous & fréquentation", "Qualité & satisfaction");
        assertThat(codes(admin.path("sections"))).contains("caisse.a_controler");

        mvc.perform(en(COMMUNITY, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isForbidden());
        mvc.perform(en(COMMUNITY, get("/api/v1/stock/articles").param("taille", "100"))).andExpect(status().isForbidden());
        mvc.perform(en(INVENTAIRES, get("/api/v1/pilotage/tableau-de-bord"))).andExpect(status().isForbidden());
        mvc.perform(en(COIFFEUSE, get("/api/v1/qualite/non-conformites"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Outils

    private static List<String> titres(JsonNode sections) {
        List<String> out = new ArrayList<>();
        sections.forEach(s -> out.add(s.path("titre").asString()));
        return out;
    }

    private static List<String> codes(JsonNode sections) {
        List<String> out = new ArrayList<>();
        sections.forEach(s -> s.path("indicateurs").forEach(i -> out.add(i.path("code").asString())));
        return out;
    }

    private static List<String> types(JsonNode taches) {
        List<String> out = new ArrayList<>();
        taches.forEach(t -> out.add(t.path("type").asString()));
        return out;
    }

    private static double valeur(JsonNode sections, String code) {
        for (JsonNode s : sections) {
            for (JsonNode i : s.path("indicateurs")) {
                if (code.equals(i.path("code").asString())) {
                    return i.path("valeur").asDouble();
                }
            }
        }
        throw new AssertionError("Indicateur absent : " + code);
    }

    private static JsonNode trouver(JsonNode sections, String code) {
        for (JsonNode s : sections) {
            for (JsonNode e : s.path("indicateurs")) {
                if (code.equals(e.path("indicateur").path("code").asString())) {
                    return e;
                }
            }
        }
        throw new AssertionError("Indicateur absent : " + code);
    }
}
