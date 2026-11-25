package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Qualité — Manager (qualité G) : contrôle qualité et hygiène, réclamations, évaluations clientes,
 * incidents et plans d'action. Coordinatrice (S/G) en appui ; coiffeuse (S limité) : signalements.
 */
@DisplayName("Qualité : contrôles, NC et actions, réclamations, signalements, hygiène, incidents")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QualiteTests extends ApiTest {

    private static final String AUJOURDHUI = LocalDate.now().toString();

    private long nombreNc(String origine) throws Exception {
        JsonNode l = lire(mvc.perform(en(MANAGER, get("/api/v1/qualite/non-conformites").param("taille", "100"))).andExpect(status().isOk()).andReturn()).path("contenu");
        long n = 0;
        for (JsonNode x : l) {
            if (origine.equals(x.path("origine").asString())) {
                n++;
            }
        }
        return n;
    }

    private Map<String, Object> controle(int note) {
        Map<String, Object> m = new HashMap<>();
        m.put("perimetre", "SKY");
        m.put("prestation", "BRUSHING");
        m.put("accueil", 4);
        m.put("technique", note);
        m.put("hygiene", 4);
        m.put("delai", 4);
        m.put("commentaire", note < 3 ? "Brushing irrégulier" : null);
        return m;
    }

    @Test
    @Order(1)
    @DisplayName("Contrôle de prestation : conforme, ou non conforme ⇒ non-conformité ouverte ; coiffeuse non habilitée")
    void controles() throws Exception {
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/controles")), controle(4)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conforme").value(true));
        long avant = nombreNc("CONTROLE");
        mvc.perform(jsonBody(en(COORDINATRICE, post("/api/v1/qualite/controles")), controle(2)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.conforme").value(false));
        assertThat(nombreNc("CONTROLE")).isEqualTo(avant + 1);
        mvc.perform(jsonBody(en(COIFFEUSE, post("/api/v1/qualite/controles")), controle(4))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/qualite/controles")), controle(4))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/controles")), controle(6))).andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    @DisplayName("Plan d'actions : le responsable réalise, une autre personne vérifie, clôture seulement si tout est vérifié")
    void planDActions() throws Exception {
        String coordinatrice = lire(mvc.perform(en(COORDINATRICE, get("/api/v1/auth/moi"))).andReturn()).path("id").asString();
        JsonNode nc = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/non-conformites")),
                        Map.of("perimetre", "SKY", "origine", "AUDIT", "gravite", "MAJEURE", "description", "Stérilisateur hors service")))
                .andExpect(status().isCreated()).andReturn());
        String id = nc.path("id").asString();
        mvc.perform(en(MANAGER, post("/api/v1/qualite/non-conformites/" + id + "/cloture"))).andExpect(status().isForbidden());
        JsonNode avecAction = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/non-conformites/" + id + "/actions")),
                        Map.of("libelle", "Remplacer le stérilisateur", "responsableId", coordinatrice, "echeance", LocalDate.now().plusDays(3).toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ACTION_EN_COURS")).andReturn());
        String action = avecAction.path("actions").get(0).path("id").asString();
        assertThat(avecAction.path("actions").get(0).path("responsable").asString()).isEqualTo("Coordination");

        mvc.perform(en(COORDINATRICE, post("/api/v1/qualite/actions/" + action + "/realisation"))).andExpect(status().isOk());
        mvc.perform(en(COORDINATRICE, post("/api/v1/qualite/actions/" + action + "/verification")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SEPARATION_DES_TACHES"));
        mvc.perform(en(MANAGER, post("/api/v1/qualite/actions/" + action + "/verification"))).andExpect(status().isOk());
        mvc.perform(en(MANAGER, post("/api/v1/qualite/non-conformites/" + id + "/cloture")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTUREE"));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/non-conformites/" + id + "/actions")), Map.of("libelle", "Encore")))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    @DisplayName("Réclamation majeure ⇒ non-conformité ; prise en charge puis résolution avec réponse obligatoire")
    void reclamations() throws Exception {
        long avant = nombreNc("RECLAMATION");
        JsonNode r = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/reclamations")), Map.of("perimetre", "KIDS",
                        "cliente", "Maman de Bintou", "canal", "WHATSAPP", "motif", "Tresses défaites le lendemain", "gravite", "MAJEURE")))
                .andExpect(status().isCreated()).andReturn());
        assertThat(nombreNc("RECLAMATION")).isEqualTo(avant + 1);
        String id = r.path("id").asString();
        mvc.perform(en(MANAGER, post("/api/v1/qualite/reclamations/" + id + "/prise-en-charge")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("EN_TRAITEMENT"));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/reclamations/" + id + "/resolution")), Map.of("reponse", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/reclamations/" + id + "/resolution")), Map.of("reponse", "Reprise offerte samedi")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("RESOLUE"));
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/avis")), Map.of("perimetre", "SKY", "cliente", "Fatou Sow", "note", 5)))
                .andExpect(status().isCreated());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/qualite/reclamations")), Map.of("perimetre", "SKY",
                "cliente", "X", "canal", "ACCUEIL", "motif", "m", "gravite", "MINEURE"))).andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("Signalement d'une coiffeuse : visible par elle et la Manager ; seule la gestion le fait avancer")
    void signalements() throws Exception {
        JsonNode s = lire(mvc.perform(jsonBody(en(COIFFEUSE, post("/api/v1/qualite/signalements")),
                        Map.of("perimetre", "SKY", "type", "BESOIN_MATERIEL", "description", "Sèche-cheveux poste 2 en panne")))
                .andExpect(status().isCreated()).andReturn());
        String id = s.path("id").asString();
        mvc.perform(en(MANAGER, get("/api/v1/pilotage/a-traiter")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].type").value("TRAITER_SIGNALEMENT"));
        mvc.perform(en(COIFFEUSE, get("/api/v1/qualite/signalements")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[?(@.id == '" + id + "')]").exists());
        mvc.perform(jsonBody(en(COIFFEUSE, patch("/api/v1/qualite/signalements/" + id + "/statut")), Map.of("statut", "TRAITE")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/qualite/signalements/" + id + "/statut")),
                        Map.of("statut", "PRIS_EN_COMPTE", "commentaire", "Pris en charge atelier")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRIS_EN_COMPTE"));
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/qualite/signalements/" + id + "/statut")),
                        Map.of("statut", "TRAITE", "commentaire", "Sèche-cheveux remplacé")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("TRAITE"));
        JsonNode detail = lire(mvc.perform(en(MANAGER, get("/api/v1/qualite/signalements").param("taille", "100")))
                .andExpect(status().isOk()).andReturn()).path("contenu");
        JsonNode vu = null;
        for (JsonNode x : detail) {
            if (id.equals(x.path("id").asString())) {
                vu = x;
            }
        }
        assertThat(vu).isNotNull();
        assertThat(vu.path("traitePar").asString()).isNotBlank();
        assertThat(vu.path("traiteLe").asString()).isNotBlank();
        assertThat(vu.path("traitements").size()).isEqualTo(2);
        assertThat(vu.path("traitements").get(0).path("statutApres").asString()).isEqualTo("PRIS_EN_COMPTE");
        assertThat(vu.path("traitements").get(0).path("commentaire").asString()).isEqualTo("Pris en charge atelier");
        assertThat(vu.path("traitements").get(1).path("statutApres").asString()).isEqualTo("TRAITE");
        assertThat(vu.path("traitements").get(1).path("traitePar").asString()).isNotBlank();
        mvc.perform(jsonBody(en(COIFFEUSE, post("/api/v1/qualite/signalements")), Map.of("perimetre", "SKY", "type", "INCONNU", "description", "x")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @Order(5)
    @DisplayName("Hygiène : points du référentiel, non conforme commenté, validation complète ⇒ NC d'hygiène ; verrouillée ensuite")
    void hygiene() throws Exception {
        JsonNode points = lire(mvc.perform(en(MANAGER, get("/api/v1/qualite/hygiene/points"))).andExpect(status().isOk()).andReturn())
                .path("FERMETURE");
        assertThat(points.size()).isPositive();
        JsonNode c = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/hygiene")), Map.of("perimetre", "SKY", "moment", "FERMETURE", "zone", "Salon")))
                .andExpect(status().isOk()).andReturn());
        String id = c.path("id").asString();
        String url = "/api/v1/qualite/hygiene/" + id;
        mvc.perform(en(MANAGER, post(url + "/validation"))).andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(MANAGER, put(url + "/points/INCONNU")), Map.of("conforme", true))).andExpect(status().isUnprocessableContent());
        String premier = points.get(0).path("code").asString();
        mvc.perform(jsonBody(en(MANAGER, put(url + "/points/" + premier)), Map.of("conforme", false))).andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(MANAGER, put(url + "/points/" + premier)), Map.of("conforme", false, "commentaire", "Poubelle non vidée")))
                .andExpect(status().isOk());
        for (int i = 1; i < points.size(); i++) {
            mvc.perform(jsonBody(en(MANAGER, put(url + "/points/" + points.get(i).path("code").asString())), Map.of("conforme", true)))
                    .andExpect(status().isOk());
        }
        long avant = nombreNc("HYGIENE");
        mvc.perform(en(MANAGER, post(url + "/validation")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conforme").value(false)).andExpect(jsonPath("$.valideeLe").isNotEmpty());
        assertThat(nombreNc("HYGIENE")).isEqualTo(avant + 1);
        mvc.perform(jsonBody(en(MANAGER, put(url + "/points/" + premier)), Map.of("conforme", true))).andExpect(status().isConflict());
        mvc.perform(en(MANAGER, get("/api/v1/qualite/hygiene").param("perimetre", "SKY").param("jour", AUJOURDHUI)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].points.length()").value(points.size()));
    }

    @Test
    @Order(6)
    @DisplayName("Incident de maintenance : déclaré, assigné, démarré, résolu, validé — dans l'ordre")
    void incidents() throws Exception {
        String manager = lire(mvc.perform(en(MANAGER, get("/api/v1/auth/moi"))).andReturn()).path("id").asString();
        JsonNode t = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/incidents")),
                        Map.of("perimetre", "SKY", "categorie", "Climatisation", "local", "Salon", "probleme", "Fuite d'eau", "urgence", "HAUTE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("DECLARE")).andReturn());
        String url = "/api/v1/qualite/incidents/" + t.path("id").asString() + "/etapes";
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("etape", "RESOUDRE"))).andExpect(status().isConflict());
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("etape", "ASSIGNER", "assigneA", manager))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("etape", "DEMARRER"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("etape", "RESOUDRE"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("etape", "VALIDER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDE"));
        mvc.perform(en(MANAGER, get("/api/v1/qualite/incidents").param("ouverts", "false"))).andExpect(status().isOk());
        // Sans local précisé : accepté (« Non précisé »)
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/qualite/incidents")), Map.of("perimetre", "SKY", "categorie", "Plomberie", "probleme", "Fuite")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.local").value("Non précisé"));
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/qualite/incidents")),
                Map.of("perimetre", "SKY", "categorie", "Eau", "probleme", "x"))).andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Contrôle d'un rendez-vous terminé : prestation marquée vérifiée, une seule fois")
    void controleDUnRendezVous() throws Exception {
        String jour = LocalDate.now().plusDays(12).toString();
        JsonNode rdv = lire(mvc.perform(jsonBody(en(MANAGER, post("/api/v1/planning/rendez-vous")),
                Map.of("perimetre", "SKY", "jour", jour, "heure", "10:00", "prestation", "BRUSHING", "cliente", "Ndèye")))
                .andExpect(status().isCreated()).andReturn());
        String id = rdv.path("id").asString();
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/statut")), Map.of("statut", "ARRIVEE")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/intention")), Map.of("prestation", "BRUSHING")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/statut")), Map.of("statut", "EN_COURS")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/cloture")),
                        Map.of("prestationRealisee", "BRUSHING", "supplements", java.util.List.of())))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, patch("/api/v1/planning/rendez-vous/" + id + "/statut")), Map.of("statut", "TERMINEE")))
                .andExpect(status().isOk());
        Map<String, Object> c = controle(4);
        c.put("rendezVousId", id);
        c.put("prestation", "AUTRE");
        mvc.perform(jsonBody(en(COORDINATRICE, post("/api/v1/qualite/controles")), c)).andExpect(status().isCreated());
        mvc.perform(en(MANAGER, get("/api/v1/planning/rendez-vous").param("du", jour).param("au", jour)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].verifieConforme").value(true));
        mvc.perform(jsonBody(en(COORDINATRICE, post("/api/v1/qualite/controles")), c)).andExpect(status().isConflict());
        JsonNode controles = lire(mvc.perform(en(MANAGER, get("/api/v1/qualite/controles").param("taille", "100").param("du", LocalDate.now().toString())
                .param("au", LocalDate.now().toString()))).andReturn()).path("contenu");
        boolean trouve = false;
        for (JsonNode x : controles) {
            trouve |= "BRUSHING".equals(x.path("prestation").asString()) && "Souadou".equals(x.path("controleur").asString());
        }
        assertThat(trouve).as("la prestation contrôlée vient du rendez-vous, pas de la saisie").isTrue();
    }
}
