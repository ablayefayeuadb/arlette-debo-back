package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sn.arlettedebo.platform.support.ApiTest;
import tools.jackson.databind.JsonNode;

/** Contrat commun des listes paginées côté serveur : page, taille bornée, total, filtres, cloisonnement. */
@DisplayName("Pagination serveur : contrat commun, bornes, filtres et cloisonnement")
class PaginationTests extends ApiTest {

    private static final String DU = LocalDate.now().minusDays(30).toString();
    private static final String AU = LocalDate.now().toString();

    private JsonNode page(String email, String url, String... params) throws Exception {
        var req = get(url);
        for (int i = 0; i + 1 < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        return lire(mvc.perform(en(email, req)).andExpect(status().isOk()).andReturn());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/iam/utilisateurs", "/api/v1/rh/employes", "/api/v1/rh/absences", "/api/v1/rh/evaluations",
            "/api/v1/qualite/non-conformites", "/api/v1/qualite/signalements", "/api/v1/qualite/incidents",
            "/api/v1/commercial/autorisations", "/api/v1/stock/articles", "/api/v1/stock/mouvements", "/api/v1/stock/comptages",
            "/api/v1/stock/commandes"})
    @DisplayName("Forme de page commune ; taille par défaut 20, bornée à 1–100")
    void contrat(String url) throws Exception {
        JsonNode p = page(choisir(url), url);
        assertThat(p.has("contenu") && p.has("page") && p.has("taille") && p.has("total") && p.has("pages")).isTrue();
        assertThat(p.path("taille").asInt()).isEqualTo(20);
        assertThat(p.path("contenu").size()).isLessThanOrEqualTo(20);
        mvc.perform(en(choisir(url), get(url).param("taille", "101"))).andExpect(status().isBadRequest());
        mvc.perform(en(choisir(url), get(url).param("taille", "0"))).andExpect(status().isBadRequest());
        mvc.perform(en(choisir(url), get(url).param("page", "-1"))).andExpect(status().isBadRequest());
        // Au-delà de la dernière page : vide, total inchangé
        JsonNode loin = page(choisir(url), url, "page", "999");
        assertThat(loin.path("contenu").size()).isZero();
        assertThat(loin.path("total").asLong()).isEqualTo(p.path("total").asLong());
    }

    /** Compte habilité pour chaque liste. */
    private static String choisir(String url) {
        if (url.contains("/rh/absences")) {
            return MANAGER;
        }
        if (url.contains("/rh/")) {
            return ADMINISTRATION;
        }
        if (url.contains("/stock/mouvements") || url.contains("/stock/comptages")) {
            return INVENTAIRES;
        }
        return url.contains("/qualite/") || url.contains("/commercial/") ? MANAGER : DG;
    }

    @Test
    @DisplayName("Pages successives : sans doublon ni oubli, total et nombre de pages cohérents")
    void pagesSuccessives() throws Exception {
        JsonNode p0 = page(DG, "/api/v1/caisse/sessions", "du", DU, "au", AU, "taille", "10");
        long total = p0.path("total").asLong();
        assertThat(total).isGreaterThan(10);
        assertThat(p0.path("pages").asInt()).isEqualTo((int) ((total + 9) / 10));
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < p0.path("pages").asInt(); i++) {
            page(DG, "/api/v1/caisse/sessions", "du", DU, "au", AU, "taille", "10", "page", String.valueOf(i))
                    .path("contenu").forEach(s -> ids.add(s.path("id").asString()));
        }
        assertThat(ids).hasSize((int) total);
        // Tri stable : jour décroissant
        List<String> jours = page(DG, "/api/v1/caisse/sessions", "du", DU, "au", AU).path("contenu").findValuesAsString("jour");
        assertThat(jours).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Comptes : recherche sans accents ni casse, profil, salon, statut")
    void filtresComptes() throws Exception {
        assertThat(page(DG, "/api/v1/iam/utilisateurs", "q", "Administration").path("contenu").findValuesAsString("email"))
                .containsExactly(ADMINISTRATION);
        assertThat(page(DG, "/api/v1/iam/utilisateurs", "profil", "CHARGE_CLIENTELE").path("contenu").findValuesAsString("email"))
                .containsExactly(CLIENTELE_KIDS);
        // Salon effectif : Binetou (salons du profil) ressort pour KIDS
        assertThat(page(DG, "/api/v1/iam/utilisateurs", "salon", "KIDS", "taille", "100").path("contenu").findValuesAsString("email"))
                .contains(CLIENTELE_KIDS).doesNotContain(CAISSE_SKY);
        assertThat(page(DG, "/api/v1/iam/utilisateurs", "statut", "DESACTIVE").path("contenu").findValuesAsString("actif"))
                .allMatch("false"::equals);
        mvc.perform(en(DG, get("/api/v1/iam/utilisateurs").param("statut", "INCONNU"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Personnel : recherche, options non paginées pour les formulaires ; filtre employé hors périmètre sans fuite")
    void personnel() throws Exception {
        assertThat(page(ADMINISTRATION, "/api/v1/rh/employes", "q", "awa").path("contenu").findValuesAsString("nom"))
                .allMatch(n -> n.toLowerCase().contains("awa"));
        JsonNode options = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/employes/options"))).andExpect(status().isOk()).andReturn());
        assertThat(options.isArray()).isTrue();
        options.forEach(o -> assertThat(o.path("perimetre").asText("")).isNotEqualTo("BOUTIQUE"));
        assertThat(options.get(0).has("salaireBase")).isFalse();
        // Employé inconnu : rien, pas d'erreur révélant une existence hors périmètre
        assertThat(page(MANAGER, "/api/v1/rh/absences", "employe", "00000000-0000-0000-0000-000000000001")
                .path("total").asLong()).isZero();
        mvc.perform(en(ADMINISTRATION, get("/api/v1/rh/absences").param("statut", "DEMANDE"))).andExpect(status().isForbidden());
        mvc.perform(en(MANAGER, get("/api/v1/rh/absences").param("statut", "DEMANDE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[*].statut").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("DEMANDE"))));
    }

    @Test
    @DisplayName("Catalogue : alertes, synthèse calculée sur tout le catalogue, liste complète pour la vente")
    void catalogue() throws Exception {
        JsonNode synthese = lire(mvc.perform(en(DG, get("/api/v1/stock/articles/synthese"))).andExpect(status().isOk()).andReturn());
        JsonNode tous = page(DG, "/api/v1/stock/articles", "taille", "100");
        assertThat(synthese.path("articles").asLong()).isEqualTo(tous.path("total").asLong());
        JsonNode alertes = page(DG, "/api/v1/stock/articles", "etat", "ALERTES", "taille", "100");
        assertThat(alertes.path("total").asLong()).isEqualTo(synthese.path("alertes").asLong());
        alertes.path("contenu").forEach(a -> assertThat(a.path("niveau").asString()).isNotEqualTo("OK"));
        JsonNode liste = lire(mvc.perform(en(BOUTIQUE, get("/api/v1/stock/articles/tous"))).andExpect(status().isOk()).andReturn());
        assertThat(liste.isArray()).isTrue();
        // Désactivés : gestion seulement
        mvc.perform(en(INVENTAIRES, get("/api/v1/stock/articles").param("etat", "INACTIFS"))).andExpect(status().isForbidden());
    }
}
