package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Planning — Manager (planning G, SKY + KIDS) et Caisse SKY (planning G, accueil) gèrent l'agenda ;
 * la coiffeuse (planning C « personnel ») ne voit et ne fait avancer que ses propres prestations.
 */
@DisplayName("Planning : rendez-vous, statuts, double réservation, créneaux")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanningTests extends ApiTest {

    /** Dans 10 jours : à l'écart des rendez-vous générés par le seeder. */
    private static final String JOUR = LocalDate.now().plusDays(10).toString();
    private static String awa;
    private static String rdv;

    private Map<String, Object> rdv(String heure, String prestation) {
        Map<String, Object> m = new HashMap<>();
        m.put("perimetre", "SKY");
        m.put("jour", JOUR);
        m.put("heure", heure);
        m.put("prestation", prestation);
        m.put("cliente", "Mariètou Diagne");
        m.put("telephone", "771234567");
        m.put("coiffeuseId", awa);
        return m;
    }

    @Test
    @Order(1)
    @DisplayName("Prise de rendez-vous à l'accueil : durée et prix du catalogue ; contrôles de périmètre et de date")
    void priseDeRendezVous() throws Exception {
        JsonNode coiffeuses = lire(mvc.perform(en(CAISSE_SKY, get("/api/v1/planning/coiffeuses").param("perimetre", "SKY")))
                .andExpect(status().isOk()).andReturn());
        awa = coiffeuses.get(0).path("id").asString();
        assertThat(coiffeuses.get(0).path("nom").asString()).isEqualTo("Coiffeur");

        JsonNode r = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/planning/rendez-vous")), rdv("10:00", "BRUSHING")))
                .andExpect(status().isCreated()).andReturn());
        rdv = r.path("id").asString();
        assertThat(r.path("dureeMin").asInt()).isEqualTo(45);
        assertThat(r.path("prix").asLong()).isEqualTo(10000);
        assertThat(r.path("coiffeuse").asString()).isEqualTo("Awa Diop");
        assertThat(r.path("statut").asString()).isEqualTo("PREVU");

        Map<String, Object> kids = rdv("11:00", "KIDS_TRESSES");
        kids.put("perimetre", "KIDS");
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/planning/rendez-vous")), kids)).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/planning/rendez-vous")), rdv("12:00", "KIDS_TRESSES")))
                .andExpect(status().isUnprocessableContent());
        Map<String, Object> passe = rdv("12:00", "BRUSHING");
        passe.put("jour", LocalDate.now().minusDays(1).toString());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/planning/rendez-vous")), passe)).andExpect(status().isBadRequest());
        mvc.perform(jsonBody(en(COIFFEUSE, post("/api/v1/planning/rendez-vous")), rdv("15:00", "BRUSHING"))).andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    @DisplayName("Double réservation de la coiffeuse refusée ; un rendez-vous annulé libère le créneau")
    void doubleReservation() throws Exception {
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")), rdv("10:30", "BRUSHING")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CRENEAU_OCCUPE"));
        JsonNode autre = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")), rdv("14:00", "BRUSHING")))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + autre.path("id").asString() + "/statut")),
                        Map.of("statut", "ANNULEE", "motif", "Cliente a reporté")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")), rdv("14:15", "BRUSHING")))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    @DisplayName("Cycle de vie : l'accueil note l'arrivée ; la coiffeuse démarre et termine SA prestation, sans pouvoir l'annuler")
    void cycleDeVie() throws Exception {
        String url = "/api/v1/planning/rendez-vous/" + rdv + "/statut";
        mvc.perform(jsonBody(en(CAISSE_SKY, patch(url)), Map.of("statut", "TERMINEE"))).andExpect(status().isConflict());
        mvc.perform(jsonBody(en(CAISSE_SKY, patch(url)), Map.of("statut", "ARRIVEE"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + rdv + "/intention")),
                        Map.of("prestation", "BRUSHING"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(COIFFEUSE, patch(url)), Map.of("statut", "ANNULEE"))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(COIFFEUSE, patch(url)), Map.of("statut", "EN_COURS"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(COIFFEUSE, patch("/api/v1/planning/rendez-vous/" + rdv + "/cloture")),
                        Map.of("prestationRealisee", "BRUSHING", "supplements", List.of())))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(COIFFEUSE, patch(url)), Map.of("statut", "TERMINEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attenteMinutes").isNumber());
        mvc.perform(en(COIFFEUSE, get("/api/v1/planning/rendez-vous").param("du", JOUR).param("au", JOUR)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + rdv + "')].statut").value("TERMINEE"));
    }

    @Test
    @Order(4)
    @DisplayName("Gestion Manager : déplacement et réaffectation limités aux rendez-vous non commencés et aux coiffeuses du salon")
    void gestionManager() throws Exception {
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + rdv + "/horaire")), Map.of("jour", JOUR, "heure", "16:00")))
                .andExpect(status().isConflict());
        JsonNode r = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")), rdv("17:00", "DEFRISAGE")))
                .andExpect(status().isCreated()).andReturn());
        String id = r.path("id").asString();
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/horaire")), Map.of("jour", JOUR, "heure", "18:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heure").value("18:00:00"));
        JsonNode kids = lire(mvc.perform(en(MANAGER, get("/api/v1/planning/coiffeuses").param("perimetre", "KIDS"))).andReturn());
        if (!kids.isEmpty()) {
            mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/coiffeuse")),
                    Map.of("coiffeuseId", kids.get(0).path("id").asString()))).andExpect(status().isUnprocessableContent());
        }
        mvc.perform(jsonBody(en(CAISSE_SKY, patch("/api/v1/planning/rendez-vous/" + id + "/horaire")), Map.of("jour", LocalDate.now().minusDays(2).toString(), "heure", "10:00")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(5)
    @DisplayName("Créneaux de réservation : ouverture, doublon refusé, fermeture, retrait ; coiffeuse non habilitée")
    void creneaux() throws Exception {
        Map<String, Object> c = Map.of("perimetre", "SKY", "jour", JOUR, "heureDebut", "09:00", "heureFin", "10:00", "capacite", 2);
        JsonNode cr = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/creneaux")), c)).andExpect(status().isCreated()).andReturn());
        String id = cr.path("id").asString();
        assertThat(cr.path("placesPrises").asLong()).isZero();
        assertThat(cr.path("placesRestantes").asLong()).isEqualTo(2);
        mvc.perform(en(CAISSE_SKY, get("/api/v1/planning/disponibilites").param("perimetre", "SKY").param("jour", JOUR).param("prestation", "BRUSHING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heure").value("09:00:00"))
                .andExpect(jsonPath("$[0].placesRestantes").value(2));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")), rdv("10:00", "BRUSHING")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/planning/creneaux")), c))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOUBLON"));
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/creneaux/" + id + "/capacite")), Map.of("capacite", 3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.capacite").value(3));
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/creneaux/" + id + "/ouverture")), Map.of("ouvert", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ouvert").value(false));
        mvc.perform(en(CAISSE_SKY, get("/api/v1/planning/creneaux").param("perimetre", "SKY").param("jour", JOUR)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/creneaux/grille")),
                Map.of("perimetre", "SKY", "jour", JOUR, "de", 9, "a", 11, "capacite", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crees").value(1));
        mvc.perform(jsonBody(en(COIFFEUSE, post("/api/v1/planning/creneaux")), c)).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/creneaux")), Map.of("perimetre", "SKY", "jour", JOUR, "heureDebut", "10:00", "heureFin", "09:00")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(en(MANAGER, delete("/api/v1/planning/creneaux/" + id))).andExpect(status().isNoContent());
    }
}
