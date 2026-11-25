package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.web.servlet.ResultActions;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Réservation en ligne publique, sans compte : la cliente choisit un créneau ouvert par le salon et laisse
 * son nom et son téléphone ; le rendez-vous arrive « prévu » dans l'agenda et dans « À traiter » de l'accueil.
 */
@DisplayName("Réservation en ligne : catalogue, disponibilités, réservation publique, garde-fous")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationEnLigneTests extends ApiTest {

    private static final String BASE = "/api/v1/public/reservation";
    private static final String JOUR = LocalDate.now().plusDays(4).toString();

    private static Map<String, Object> demande(String heure, String prestation, String telephone) {
        Map<String, Object> m = new HashMap<>();
        m.put("perimetre", "KIDS");
        m.put("jour", JOUR);
        m.put("heure", heure);
        m.put("prestation", prestation);
        m.put("nom", "  Fatou   Sarr ");
        m.put("telephone", telephone);
        m.put("note", "Première visite");
        return m;
    }

    private ResultActions reserver(Map<String, Object> d) throws Exception {
        return mvc.perform(jsonBody(post(BASE), d));
    }

    private ResultActions disponibilites() throws Exception {
        return mvc.perform(get(BASE + "/disponibilites").param("perimetre", "KIDS").param("jour", JOUR).param("prestation", "KIDS_TRESSES"));
    }

    private void ouvrir(String debut, String fin, int capacite) throws Exception {
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/creneaux")),
                Map.of("perimetre", "KIDS", "jour", JOUR, "heureDebut", debut, "heureFin", fin, "capacite", capacite)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(1)
    @DisplayName("Sans connexion : catalogue et créneaux ouverts par le salon ; le reste de l'API reste fermé")
    void catalogueEtDisponibilites() throws Exception {
        mvc.perform(get(BASE + "/prestations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'BRUSHING')].perimetre").value("SKY"))
                .andExpect(jsonPath("$[?(@.code == 'KIDS_TRESSES')].prix").value(12000));
        ouvrir("07:00", "08:00", 1);
        disponibilites().andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.heure == '07:00:00')].placesRestantes").value(1));
        mvc.perform(get(BASE + "/disponibilites").param("perimetre", "SKY").param("jour", JOUR).param("prestation", "KIDS_TRESSES"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/planning/rendez-vous").param("du", JOUR).param("au", JOUR)).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("Demandes refusées : téléphone invalide, jour passé ou trop lointain, heure hors créneau, champs manquants")
    void refus() throws Exception {
        reserver(demande("07:00", "KIDS_TRESSES", "12345")).andExpect(status().isUnprocessableContent());
        Map<String, Object> passe = demande("07:00", "KIDS_TRESSES", "77 600 11 22");
        passe.put("jour", LocalDate.now().minusDays(1).toString());
        reserver(passe).andExpect(status().isUnprocessableContent());
        Map<String, Object> loin = demande("07:00", "KIDS_TRESSES", "77 600 11 22");
        loin.put("jour", LocalDate.now().plusDays(90).toString());
        reserver(loin).andExpect(status().isUnprocessableContent());
        reserver(demande("07:30", "KIDS_TRESSES", "77 600 11 22")).andExpect(status().isConflict());
        Map<String, Object> sansNom = demande("07:00", "KIDS_TRESSES", "77 600 11 22");
        sansNom.remove("nom");
        reserver(sansNom).andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("Réservation : récapitulatif sans donnée de tiers, créneau complet ensuite, rendez-vous « prévu » en ligne")
    void reservation() throws Exception {
        JsonNode c = lire(reserver(demande("07:00", "KIDS_TRESSES", "+221 77 600 11 22")).andExpect(status().isCreated()).andReturn());
        assertThat(c.path("reference").asString()).matches("[0-9A-F]{8}");
        String reference = c.path("reference").asString();
        assertThat(c.path("prestation").asString()).isEqualTo("Tresses enfant");
        assertThat(c.path("prix").asLong()).isEqualTo(12000);
        assertThat(c.has("telephone")).isFalse();
        disponibilites().andExpect(jsonPath("$[?(@.heure == '07:00:00')].placesRestantes").value(0));
        reserver(demande("07:00", "KIDS_TRESSES", "77 600 33 44")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ce créneau vient d'être réservé : choisissez-en un autre."));
        mvc.perform(en(CLIENTELE_KIDS, get("/api/v1/planning/rendez-vous").param("du", JOUR).param("au", JOUR).param("perimetre", "KIDS")))
                .andExpect(jsonPath("$[?(@.cliente == 'Fatou Sarr')].source").value("EN_LIGNE"))
                .andExpect(jsonPath("$[?(@.cliente == 'Fatou Sarr')].statut").value("PREVU"))
                .andExpect(jsonPath("$[?(@.cliente == 'Fatou Sarr')].telephone").value("776001122"))
                .andExpect(jsonPath("$[?(@.cliente == 'Fatou Sarr')].reference").value(reference));
    }

    @Test
    @Order(4)
    @DisplayName("« À traiter » : l'accueil Kids voit la réservation à confirmer ; l'accueil Sky non")
    void aTraiter() throws Exception {
        mvc.perform(en(CLIENTELE_KIDS, get("/api/v1/pilotage/a-traiter")))
                .andExpect(jsonPath("$[?(@.type == 'CONFIRMER_RDV_EN_LIGNE')].detail").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.startsWith("Fatou Sarr · KIDS_TRESSES"))));
        mvc.perform(en(CAISSE_SKY, get("/api/v1/pilotage/a-traiter")))
                .andExpect(jsonPath("$[?(@.type == 'CONFIRMER_RDV_EN_LIGNE')].perimetre").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("SKY"))))
                .andExpect(jsonPath("$[?(@.detail =~ /Fatou Sarr.*/)]").isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("Plafond : deux rendez-vous à venir au plus par numéro")
    void plafond() throws Exception {
        ouvrir("09:00", "10:00", 5);
        reserver(demande("09:00", "KIDS_SOIN", "77 600 11 22")).andExpect(status().isCreated());
        reserver(demande("09:00", "KIDS_SOIN", "776001122")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESERVATIONS_MAX"));
    }
}
