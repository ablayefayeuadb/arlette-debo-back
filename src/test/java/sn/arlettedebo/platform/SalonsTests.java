package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import sn.arlettedebo.platform.support.ApiTest;
import tools.jackson.databind.JsonNode;

/**
 * Salons gérés par la DG et la Manager ; mêmes pouvoirs de direction sur les comptes.
 * Le salon créé ici est désactivé en fin de test : sans effet sur les autres classes de test.
 */
@DisplayName("Salons : création DG/Manager, comptes et désactivation")
class SalonsTests extends ApiTest {

    private static final String MDP_PROVISOIRE = "Provisoire@2026x";

    private JsonNode moi(String bearer) throws Exception {
        return lire(mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk()).andReturn());
    }

    private static List<String> codes(JsonNode tableau, String champ) {
        return tableau.findValuesAsString(champ);
    }

    /** Connexion avec un mot de passe provisoire puis changement obligatoire : renvoie le jeton de la nouvelle session. */
    private String premiereConnexion(String email, String nouveau) throws Exception {
        String provisoire = "Bearer " + lire(mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", email, "motDePasse", MDP_PROVISOIRE)))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asString();
        return "Bearer " + lire(mvc.perform(jsonBody(post("/api/v1/auth/mot-de-passe"), Map.of("actuel", MDP_PROVISOIRE, "nouveau", nouveau))
                        .header(HttpHeaders.AUTHORIZATION, provisoire))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asString();
    }

    private MockHttpServletRequestBuilder avec(String bearer, MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    @Test
    @DisplayName("DG et Manager : créent un salon ; Manager gère les comptes comme la DG")
    @SuppressWarnings("java:S5961") // scénario bout-en-bout salons/comptes : assertions regroupées volontairement
    void salonEtResponsable() throws Exception {
        // ------------------------------------------------ Création du salon (direction)
        mvc.perform(jsonBody(en(DO, post("/api/v1/referentiels/salons")), Map.of("libelle", "Sky Almadies", "type", "SALON")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")), Map.of("libelle", "Sky Almadies", "type", "SPA")))
                .andExpect(status().isBadRequest());
        JsonNode salon = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/referentiels/salons")), Map.of("libelle", "  Sky  Almadiès ", "type", "SALON")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Sky Almadiès"))
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.capaciteCreneau").value(1)).andReturn());
        String code = salon.path("code").asString();
        assertThat(code).isEqualTo("SKY_ALMADIES");
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/referentiels/salons/" + code)),
                Map.of("libelle", "Sky Almadiès", "actif", true, "capaciteCreneau", 2, "heureOuverture", 9, "heureFermeture", 18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaciteCreneau").value(2))
                .andExpect(jsonPath("$.heureOuverture").value(9))
                .andExpect(jsonPath("$.heureFermeture").value(18));
        mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")), Map.of("libelle", "sky almadiès", "type", "SALON")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALON_EXISTANT"));
        assertThat(codes(lire(mvc.perform(get("/api/v1/public/salons")).andExpect(status().isOk()).andReturn()), "code")).contains(code);

        // Profils transverses : le voient tout de suite ; profils d'un salon : non
        oublierJeton(MANAGER);
        oublierJeton(CAISSE_SKY);
        oublierJeton(BOUTIQUE);
        assertThat(moi("Bearer " + jeton(MANAGER)).path("perimetres").toString()).contains(code);
        assertThat(moi("Bearer " + jeton(DG)).path("perimetres").toString()).contains(code);
        assertThat(moi("Bearer " + jeton(CAISSE_SKY)).path("perimetres").toString()).doesNotContain(code);
        assertThat(moi("Bearer " + jeton(BOUTIQUE)).path("perimetres").toString()).doesNotContain(code);

        // ------------------------------------------------ Compte Manager rattaché au nouveau salon
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/iam/utilisateurs")),
                        Map.of("nom", "Manager générale", "email", "manager.generale@arlettedebo.sn", "profil", "MANAGER", "telephone", "77 100 00 01")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.compte.perimetres.length()").value(0));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Ndeye Responsable", "email", "ndeye@arlettedebo.sn",
                        "profil", "MANAGER", "perimetres", List.of(code), "motDePasse", MDP_PROVISOIRE, "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        String resp = premiereConnexion("ndeye@arlettedebo.sn", "Almadies@Salon27");
        assertThat(moi(resp).path("perimetres")).hasToString("[\"" + code + "\"]");

        // Création d'un caissier rattaché au salon
        Map<String, Object> caisse = new HashMap<>(Map.of("nom", "Caisse Almadies", "email", "caisse.almadies@arlettedebo.sn",
                "profil", "CAISSIER", "perimetres", List.of(code), "telephone", "77 100 00 01"));
        JsonNode cree = lire(mvc.perform(jsonBody(avec(resp, post("/api/v1/iam/utilisateurs")), caisse))
                .andExpect(status().isCreated()).andReturn());
        String idCaisse = cree.path("compte").path("id").asString();
        assertThat(cree.path("compte").path("perimetres")).hasToString("[\"" + code + "\"]");
        assertThat(cree.path("motDePasseProvisoire").asString()).isNotBlank();

        // Manager : tous les profils, tous les salons (comme la DG)
        mvc.perform(jsonBody(avec(resp, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Coord Almadies", "email", "coord.almadies@arlettedebo.sn",
                        "profil", "COORDINATION", "perimetres", List.of(code), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        mvc.perform(jsonBody(avec(resp, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Coiffeuse Sky", "email", "coif.sky.almadies@arlettedebo.sn",
                        "profil", "COIFFEUR", "perimetres", List.of("SKY"), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());

        // Liste : tous les comptes
        JsonNode liste = lire(mvc.perform(avec(resp, get("/api/v1/iam/utilisateurs").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        assertThat(codes(liste, "email")).contains("caisse.almadies@arlettedebo.sn", DG, MANAGER, BOUTIQUE);

        // Gestion : mot de passe, désactivation, réactivation, profil, salons, relais
        mvc.perform(avec(resp, post("/api/v1/iam/utilisateurs/" + idCaisse + "/reinitialisation-mot-de-passe")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.motDePasseProvisoire").isNotEmpty());
        mvc.perform(avec(resp, post("/api/v1/iam/utilisateurs/" + idCaisse + "/desactivation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        mvc.perform(avec(resp, post("/api/v1/iam/utilisateurs/" + idCaisse + "/reactivation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        mvc.perform(jsonBody(avec(resp, patch("/api/v1/iam/utilisateurs/" + idCaisse + "/profil")), Map.of("profil", "COIFFEUR")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(avec(resp, put("/api/v1/iam/utilisateurs/" + idCaisse + "/perimetres")), Map.of("perimetres", List.of(code, "SKY"))))
                .andExpect(status().isOk());
        mvc.perform(avec(resp, get("/api/v1/iam/delegations"))).andExpect(status().isOk());

        // ------------------------------------------------ Renommage puis désactivation (Manager)
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/referentiels/salons/" + code)), Map.of("libelle", "Sky Club Almadies", "actif", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.libelle").value("Sky Club Almadies"));
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/referentiels/salons/" + code)), Map.of("libelle", "Sky Club", "actif", true)))
                .andExpect(status().isConflict());
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/referentiels/salons/" + code)), Map.of("libelle", "Sky Club Almadies", "actif", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        // Désactivé : plus aucun accès, plus dans la liste publique, toujours listé pour la direction
        assertThat(moi(resp).path("perimetres").size()).isZero();
        assertThat(moi("Bearer " + jeton(MANAGER)).path("perimetres").toString()).doesNotContain(code);
        assertThat(codes(lire(mvc.perform(get("/api/v1/public/salons")).andReturn()), "code")).doesNotContain(code);
        assertThat(codes(lire(mvc.perform(en(MANAGER, get("/api/v1/referentiels/salons"))).andReturn()), "code")).contains(code);
    }

    @Test
    @DisplayName("Un seul caissier par salon : création, changement de salons et réactivation refusés si le salon a déjà le sien")
    void unCaissierParSalon() throws Exception {
        // Kids Club : chargé clientèle encaisse déjà ; Sky Club : caissier du profil
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Caisse Kids", "email", "caisse.kids@arlettedebo.sn",
                        "profil", "CAISSIER", "perimetres", List.of("KIDS"), "telephone", "77 100 00 01")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UN_CAISSIER_PAR_SALON"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Chargé clientèle")));
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Caisse Sky 2", "email", "caisse.sky2@arlettedebo.sn",
                        "profil", "CAISSIER", "telephone", "77 100 00 01")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Caissier")));

        // Un même caissier peut tenir plusieurs salons… sauf ceux qui ont déjà le leur
        String caissier = null;
        for (JsonNode c : lire(mvc.perform(en(DG, get("/api/v1/iam/utilisateurs").param("q", "caissier@"))).andReturn()).path("contenu")) {
            caissier = c.path("id").asString();
        }
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/utilisateurs/" + caissier + "/perimetres")), Map.of("perimetres", List.of("SKY", "KIDS"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UN_CAISSIER_PAR_SALON"));

        // Nouveau salon : un premier caissier accepté, un second refusé ; réactivation refusée si le poste est repris
        String code = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")), Map.of("libelle", "Salon Caisse Unique", "type", "SALON")))
                .andExpect(status().isCreated()).andReturn()).path("code").asString();
        String premier = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Premier Caissier",
                        "email", "premier.caissier@arlettedebo.sn", "profil", "CAISSIER", "perimetres", List.of(code), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated()).andReturn()).path("compte").path("id").asString();
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Second Caissier",
                        "email", "second.caissier@arlettedebo.sn", "profil", "CHARGE_CLIENTELE", "perimetres", List.of(code), "telephone", "77 100 00 01")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Premier Caissier")));
        // Vendeur boutique (magasins autonomes) : n'encaisse pas → ne concurrence pas le caissier du salon
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Vendeur En Plus",
                        "email", "vendeur.plus@arlettedebo.sn", "profil", "VENDEUR_BOUTIQUE", "perimetres", List.of(), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        mvc.perform(en(DG, post("/api/v1/iam/utilisateurs/" + premier + "/desactivation"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Remplaçant",
                        "email", "remplacant.caissier@arlettedebo.sn", "profil", "CAISSIER", "perimetres", List.of(code), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        mvc.perform(en(DG, post("/api/v1/iam/utilisateurs/" + premier + "/reactivation")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UN_CAISSIER_PAR_SALON"));
        // Non-caissiers (coiffeuse) : aucune limite
        mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Coiffeuse En Plus",
                        "email", "coiffeuse.plus@arlettedebo.sn", "profil", "COIFFEUR", "perimetres", List.of(code), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        mvc.perform(jsonBody(en(DG, put("/api/v1/referentiels/salons/" + code)), Map.of("libelle", "Salon Caisse Unique", "actif", false)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Boutique autonome : vendeur désigné à la création ; direction aussi ; sans vendeur → refus")
    void boutiqueAutonome() throws Exception {
        mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")), Map.of("libelle", "Boutique Orpheline", "type", "BOUTIQUE")))
                .andExpect(status().isUnprocessableContent());
        String vendeurId = moi("Bearer " + jeton(BOUTIQUE)).path("id").asText();
        JsonNode b = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")),
                        Map.of("libelle", "Boutique Plateau", "type", "BOUTIQUE", "responsableId", vendeurId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BOUTIQUE"))
                .andExpect(jsonPath("$.libelle").value("Boutique Plateau")).andReturn());
        String code = b.path("code").asString();
        assertThat(code).isEqualTo("BOUTIQUE_PLATEAU");
        JsonNode publics = lire(mvc.perform(get("/api/v1/public/salons")).andExpect(status().isOk()).andReturn());
        assertThat(codes(publics, "code")).contains(code);
        publics.forEach(n -> {
            if (code.equals(n.path("code").asText())) {
                assertThat(n.path("type").asText()).isEqualTo("BOUTIQUE");
            }
        });
        oublierJeton(BOUTIQUE);
        oublierJeton(MANAGER);
        assertThat(moi("Bearer " + jeton(BOUTIQUE)).path("perimetres").toString()).contains(code);
        assertThat(moi("Bearer " + jeton(MANAGER)).path("perimetres").toString()).contains(code);
        assertThat(moi("Bearer " + jeton(CAISSE_SKY)).path("perimetres").toString()).doesNotContain(code);
        // Un caissier ne peut pas être désigné responsable
        String caisseId = moi("Bearer " + jeton(CAISSE_SKY)).path("id").asText();
        mvc.perform(jsonBody(en(DG, post("/api/v1/referentiels/salons")),
                        Map.of("libelle", "Boutique Pirate", "type", "BOUTIQUE", "responsableId", caisseId)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Manager : tous les comptes et tous les profils, comme la DG")
    void managerDirection() throws Exception {
        List<String> emails = codes(lire(mvc.perform(en(MANAGER, get("/api/v1/iam/utilisateurs").param("taille", "100"))).andExpect(status().isOk())
                .andReturn()).path("contenu"), "email");
        assertThat(emails).contains(COIFFEUSE, CAISSE_SKY, CLIENTELE_KIDS, BOUTIQUE, DG, MANAGER, ADMINISTRATION, COORDINATRICE);
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/iam/utilisateurs")), Map.of("nom", "Coord Test", "email", "coord.test.mgr@arlettedebo.sn",
                        "profil", "COORDINATION", "perimetres", List.of("SKY"), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated());
        mvc.perform(en(MANAGER, get("/api/v1/journal").param("taille", "5"))).andExpect(status().isOk());
    }
}
