package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.arlettedebo.platform.support.ApiTest;

@DisplayName("RH : périmètres, confidentialité de la paie, congés, pointage")
class RhTests extends ApiTest {

    @Test
    @DisplayName("Le seeder crée un dossier lié à chaque compte opérationnel")
    void dossiersSeedes() throws Exception {
        JsonNode liste = lire(mvc.perform(en(ADMINISTRATION, get("/api/v1/rh/employes").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        assertThat(liste.size()).isGreaterThanOrEqualTo(9);
        liste.forEach(e -> assertThat(e.path("matricule").asString()).startsWith("AD-"));
    }

    @Test
    @DisplayName("Manager : mêmes consultations RH que la DG (salaires, variables de paie, absences)")
    void managerCommeDg() throws Exception {
        JsonNode liste = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/employes").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        assertThat(liste.size()).isPositive();
        assertThat(liste.get(0).has("salaireBase")).isTrue();
        mvc.perform(en(MANAGER, get("/api/v1/rh/paie/variables").param("mois", YearMonth.now().toString())))
                .andExpect(status().isOk());
        mvc.perform(en(MANAGER, get("/api/v1/rh/absences"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/absences")),
                Map.of("employeId", liste.get(0).path("id").asString(), "type", "ABSENCE", "du", LocalDate.now().toString(), "au", LocalDate.now().toString())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DG : salaires et absences visibles ; saisie d'absence autorisée")
    void dgConsultation() throws Exception {
        JsonNode liste = lire(mvc.perform(en(DG, get("/api/v1/rh/employes").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        assertThat(liste.get(0).has("salaireBase")).isTrue();
        mvc.perform(en(DG, get("/api/v1/rh/paie/variables").param("mois", YearMonth.now().toString()))).andExpect(status().isOk());
        mvc.perform(en(DG, get("/api/v1/rh/absences"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(DG, post("/api/v1/rh/absences")),
                Map.of("employeId", liste.get(0).path("id").asString(), "type", "ABSENCE",
                        "du", LocalDate.now().plusDays(1).toString(), "au", LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Administration : dossiers et paie ; absences interdites — congés décidés par Manager")
    void gestionRh() throws Exception {
        JsonNode e = lire(mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/rh/employes")), Map.of(
                "nom", "Fatou Test", "poste", "Coiffeuse", "perimetre", "KIDS", "telephone", "77 000 00 00",
                "dateEntree", "2026-01-05", "contrat", "CDD", "salaireBase", 120000)))
                .andExpect(status().isCreated()).andReturn());
        String id = e.path("id").asString();

        mvc.perform(en(ADMINISTRATION, get("/api/v1/rh/absences"))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/rh/absences")), Map.of(
                "employeId", id, "type", "CONGE", "du", "2026-12-20", "au", "2026-12-24", "motif", "Congé annuel")))
                .andExpect(status().isForbidden());

        JsonNode conge = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/absences")), Map.of(
                "employeId", id, "type", "CONGE", "du", "2026-12-20", "au", "2026-12-24", "motif", "Congé annuel")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("DEMANDE")).andReturn());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/absences/" + conge.path("id").asString() + "/decision")), Map.of("approuver", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("APPROUVEE"));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/absences/" + conge.path("id").asString() + "/decision")), Map.of("approuver", false)))
                .andExpect(status().isConflict());

        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/absences")), Map.of(
                "employeId", id, "type", "MALADIE", "du", "2026-12-26", "au", "2026-12-25")))
                .andExpect(status().isUnprocessableContent());

        String mois = YearMonth.now().toString();
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/rh/paie/variables")), Map.of(
                "employeId", id, "mois", mois, "type", "PRIME", "montant", 15000, "commentaire", "Objectif atteint")))
                .andExpect(status().isCreated());
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/rh/paie/transmission")), Map.of("mois", mois)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignesTransmises").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // N+1 : Fatou (KIDS) est notée par la Manager, pas par l'Administration
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/rh/evaluations")), Map.of(
                "employeId", id, "note", 4, "pointsForts", "Ponctuelle", "axesProgres", "Coiffures enfants")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EVALUATION_N_PLUS_1"));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/evaluations")), Map.of(
                "employeId", id, "note", 4, "pointsForts", "Ponctuelle", "axesProgres", "Coiffures enfants")))
                .andExpect(status().isCreated());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/rh/evaluations")), Map.of("employeId", id, "note", 7)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Matrice des compétences reliée au catalogue des prestations")
    void competences() throws Exception {
        JsonNode liste = lire(mvc.perform(en(ADMINISTRATION, get("/api/v1/rh/employes").param("taille", "100"))).andReturn()).path("contenu");
        String id = liste.get(0).path("id").asString();
        mvc.perform(jsonBody(en(ADMINISTRATION, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/rh/employes/" + id + "/competences/NAPPY")), Map.of("niveau", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.competences.NAPPY").value(2));
        mvc.perform(jsonBody(en(ADMINISTRATION, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/rh/employes/" + id + "/competences/INCONNUE")), Map.of("niveau", 2)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(ADMINISTRATION, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/rh/employes/" + id + "/competences/NAPPY")), Map.of("niveau", 4)))
                .andExpect(status().isBadRequest());
        // Consultation seule pour le Manager (RH « limité ») et la DG
        mvc.perform(jsonBody(en(MANAGER, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/rh/employes/" + id + "/competences/NAPPY")), Map.of("niveau", 3)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DG, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/rh/employes/" + id + "/competences/NAPPY")), Map.of("niveau", 3)))
                .andExpect(status().isForbidden());
    }
}
