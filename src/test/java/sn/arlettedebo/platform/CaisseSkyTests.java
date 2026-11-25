package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import sn.arlettedebo.platform.support.ApiTest;

/**
 * Profil Caisse SKY — matrice : caisse S/G · planning G · qualité C · tableau de bord C (limité), périmètre SKY.
 * Fonctionnalités : encaissements, justificatifs, clôture quotidienne, accueil, suivi prospects et clientes,
 * promotion des prestations et abonnements.
 */
@DisplayName("Caisse SKY : journée de caisse, clientes, abonnements, gestes commerciaux")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaisseSkyTests extends ApiTest {

    private static String session;
    private static String cliente;
    private static String encaissementAAnnuler;
    private static String piece;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private MockMultipartHttpServletRequestBuilder depot(String sessionId, String nature, byte[] contenu, String nom) {
        MockMultipartHttpServletRequestBuilder r = multipart("/api/v1/caisse/sessions/" + sessionId + "/justificatifs");
        r.file(new MockMultipartFile("fichier", nom, "application/octet-stream", contenu));
        r.param("nature", nature);
        return r;
    }

    private JsonNode duJour(String email, String perimetre) throws Exception {
        return lire(mvc.perform(en(email, get("/api/v1/caisse/sessions/du-jour").param("perimetre", perimetre)))
                .andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> encaissement(String libelle, long montant, String mode) {
        Map<String, Object> m = new HashMap<>();
        m.put("libelle", libelle);
        m.put("montant", montant);
        m.put("modePaiement", mode);
        return m;
    }

    @Test
    @Order(1)
    @DisplayName("Session du jour : une seule par salon et par jour ; hors périmètre interdit")
    void sessionDuJour() throws Exception {
        JsonNode j = duJour(CAISSE_SKY, "SKY");
        assertThat(j.path("session").path("statut").asString()).isEqualTo("OUVERTE");
        session = j.path("session").path("id").asString();
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions")), Map.of("perimetre", "SKY", "fondOuverture", 20000)))
                .andExpect(status().isConflict());
        mvc.perform(en(CAISSE_SKY, get("/api/v1/caisse/sessions/du-jour").param("perimetre", "KIDS")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions")), Map.of("perimetre", "KIDS", "fondOuverture", 20000)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/caisse/sessions")), Map.of("perimetre", "SKY", "fondOuverture", 20000)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    @DisplayName("Fiche prospect : téléphone normalisé, doublon refusé, recherche par nom et par numéro")
    void prospect() throws Exception {
        JsonNode c = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/clients")), Map.of("perimetre", "SKY",
                        "nom", "Ndèye Fall", "telephone", "+221 77 555 66 77", "source", "Instagram", "consentementMarketing", true)))
                .andExpect(status().isCreated()).andReturn());
        cliente = c.path("id").asString();
        assertThat(c.path("telephone").asString()).isEqualTo("775556677");
        assertThat(c.path("statut").asString()).isEqualTo("PROSPECT");
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/clients")), Map.of("perimetre", "SKY", "nom", "Autre", "telephone", "77 555 66 77")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOUBLON"));
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/clients")), Map.of("perimetre", "KIDS", "nom", "Hors périmètre")))
                .andExpect(status().isForbidden());
        mvc.perform(en(CAISSE_SKY, get("/api/v1/commercial/clients").param("recherche", "Ndeye Fal")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu[0].id").value(cliente));
        mvc.perform(en(CAISSE_SKY, get("/api/v1/commercial/clients").param("recherche", "77 555 66 77")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenu.length()").value(1));
        mvc.perform(jsonBody(en(CAISSE_SKY, patch("/api/v1/commercial/clients/" + cliente + "/notes")), Map.of("preferences", "Brushing lisse")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferences").value("Brushing lisse"));
        // Manager : consultation (caisse C) mais pas de saisie
        mvc.perform(en(MANAGER, get("/api/v1/commercial/clients"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/commercial/clients")), Map.of("perimetre", "SKY", "nom", "X")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("Encaissement rattaché à la cliente : visite enregistrée, prospect devenu cliente ; mode inconnu refusé")
    void encaissement() throws Exception {
        Map<String, Object> e = encaissement("Brushing", 10000, "WAVE");
        e.put("clientId", cliente);
        e.put("note", "Réf. Wave 8841");
        JsonNode r = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), e))
                .andExpect(status().isCreated()).andReturn());
        assertThat(r.path("libelle").asString()).isEqualTo("Brushing · Ndèye Fall");
        assertThat(r.path("encaissePar").asString()).isEqualTo("Caissier");
        mvc.perform(en(CAISSE_SKY, get("/api/v1/commercial/clients/" + cliente)))
                .andExpect(jsonPath("$.statut").value("CLIENTE")).andExpect(jsonPath("$.derniereVisite").isNotEmpty());

        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), encaissement("X", 1000, "BITCOIN")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), encaissement("X", 0, "ESPECES")))
                .andExpect(status().isBadRequest());
        JsonNode aAnnuler = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")),
                encaissement("Défrisage", 18000, "ESPECES"))).andExpect(status().isCreated()).andReturn());
        encaissementAAnnuler = aAnnuler.path("id").asString();
    }

    @Test
    @Order(4)
    @DisplayName("Annulation : motif obligatoire, gestion caisse (G) uniquement")
    void annulation() throws Exception {
        String url = "/api/v1/caisse/encaissements/" + encaissementAAnnuler + "/annulation";
        mvc.perform(jsonBody(en(MANAGER, post(url)), Map.of("motif", "erreur"))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(CAISSE_SKY, post(url)), Map.of("motif", " "))).andExpect(status().isBadRequest());
        mvc.perform(jsonBody(en(CAISSE_SKY, post(url)), Map.of("motif", "Doublon de saisie")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.annule").value(true))
                .andExpect(jsonPath("$.note").value("Annulé : Doublon de saisie"));
        mvc.perform(jsonBody(en(CAISSE_SKY, post(url)), Map.of("motif", "encore"))).andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    @DisplayName("Abonnement : formule du périmètre, cliente abonnée, séances décomptées")
    void abonnement() throws Exception {
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/clients/" + cliente + "/abonnements")), Map.of("formule", "KIDS_MENSUEL")))
                .andExpect(status().isUnprocessableContent());
        JsonNode a = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/clients/" + cliente + "/abonnements")),
                Map.of("formule", "SKY_BRUSHING_4"))).andExpect(status().isCreated()).andReturn());
        assertThat(a.path("seancesRestantes").asInt()).isEqualTo(4);
        mvc.perform(en(CAISSE_SKY, post("/api/v1/commercial/abonnements/" + a.path("id").asString() + "/seance")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seancesRestantes").value(3));
        mvc.perform(en(CAISSE_SKY, get("/api/v1/commercial/clients/" + cliente)))
                .andExpect(jsonPath("$.statut").value("ABONNEE")).andExpect(jsonPath("$.abonnements.length()").value(1));
    }

    @Test
    @Order(6)
    @DisplayName("Geste commercial : demandé par la caisse, autorisé par la DG, appliqué une seule fois à l'encaissement")
    void gesteCommercial() throws Exception {
        JsonNode d = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/commercial/autorisations")), Map.of("perimetre", "SKY",
                        "type", "REMISE", "clientId", cliente, "cliente", "Ndèye Fall", "montant", 2000, "motif", "Fidélité")))
                .andExpect(status().isCreated()).andReturn());
        String id = d.path("id").asString();
        Map<String, Object> e = encaissement("Brushing", 8000, "ESPECES");
        e.put("demandeAutorisationId", id);
        // Non encore autorisé : refusé
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), e))
                .andExpect(status().isConflict());
        mvc.perform(jsonBody(en(DG, post("/api/v1/commercial/autorisations/" + id + "/decision")), Map.of("autoriser", true)))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), e))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.note").value("geste commercial 2000 FCFA"));
        mvc.perform(en(CAISSE_SKY, get("/api/v1/commercial/autorisations")))
                .andExpect(jsonPath("$.contenu[?(@.id == '" + id + "')].statut").value("APPLIQUEE"));
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), e))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    @DisplayName("Justificatifs : reçu Wave rattaché à l'encaissement, type réel vérifié, salon contrôlé, retrait par la déposante")
    void justificatifs() throws Exception {
        String wave = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")),
                encaissement("Brushing", 8000, "WAVE"))).andExpect(status().isCreated()).andReturn()).path("id").asString();
        long manquantsAvant = duJour(CAISSE_SKY, "SKY").path("session").path("paiementsSansJustificatif").asLong();
        assertThat(manquantsAvant).isPositive();

        JsonNode j = lire(mvc.perform(en(CAISSE_SKY, depot(session, "RECU_MOBILE", PNG, "C:\\Photos\\recu-wave.png")
                        .param("encaissementId", wave).param("commentaire", "Réf. W-8841")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeMime").value("image/png"))
                .andExpect(jsonPath("$.nomFichier").value("recu-wave.png"))
                .andExpect(jsonPath("$.deposePar").value("Khady"))
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.hasLength(64))).andReturn());
        piece = j.path("id").asString();
        JsonNode jour = duJour(CAISSE_SKY, "SKY");
        assertThat(jour.path("session").path("paiementsSansJustificatif").asLong()).isEqualTo(manquantsAvant - 1);
        assertThat(jour.path("justificatifs").findValuesAsString("encaissementId")).contains(wave);

        // Faux PDF, nature inconnue, encaissement annulé
        mvc.perform(en(CAISSE_SKY, depot(session, "TICKET_CARTE", "pas un pdf".getBytes(), "ticket.pdf")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(en(CAISSE_SKY, depot(session, "SELFIE", PNG, "x.png"))).andExpect(status().isBadRequest());
        mvc.perform(en(CAISSE_SKY, depot(session, "AUTRE", PNG, "x.png").param("encaissementId", encaissementAAnnuler)))
                .andExpect(status().isConflict());

        // Droits : la Manager consulte et télécharge sans déposer ; Binetou (KIDS) ne voit pas le salon SKY
        mvc.perform(en(MANAGER, depot(session, "AUTRE", PNG, "x.png"))).andExpect(status().isForbidden());
        mvc.perform(en(CLIENTELE_KIDS, depot(session, "AUTRE", PNG, "x.png"))).andExpect(status().isForbidden());
        mvc.perform(en(CLIENTELE_KIDS, get("/api/v1/caisse/justificatifs/" + piece))).andExpect(status().isForbidden());
        mvc.perform(en(MANAGER, get("/api/v1/caisse/justificatifs/" + piece)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-SHA256", j.path("sha256").asString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(PNG));
        mvc.perform(en(MANAGER, get("/api/v1/caisse/sessions/" + session + "/justificatifs")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nature").value("RECU_MOBILE"));

        // Retrait d'une pièce déposée par erreur (caisse ouverte)
        String erreur = lire(mvc.perform(en(CAISSE_SKY, depot(session, "FICHE_COMPTAGE", PNG, "erreur.png")))
                .andExpect(status().isCreated()).andReturn()).path("id").asString();
        mvc.perform(en(MANAGER, delete("/api/v1/caisse/justificatifs/" + erreur))).andExpect(status().isForbidden());
        mvc.perform(en(CAISSE_SKY, delete("/api/v1/caisse/justificatifs/" + erreur))).andExpect(status().isNoContent());
        mvc.perform(en(CAISSE_SKY, get("/api/v1/caisse/justificatifs/" + erreur))).andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("Clôture : écart calculé sur les espèces ; plus d'encaissement ni d'annulation ; contrôle indépendant par la Manager")
    void cloture() throws Exception {
        JsonNode avant = duJour(CAISSE_SKY, "SKY");
        long fond = avant.path("session").path("fondOuverture").asLong();
        long especes = 0;
        for (JsonNode e : avant.path("encaissements")) {
            if (!e.path("annule").asBoolean() && "ESPECES".equals(e.path("mode").asString())) {
                especes += e.path("montant").asLong();
            }
        }
        JsonNode c = lire(mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/cloture")),
                Map.of("montantCompte", fond + especes - 500))).andExpect(status().isOk()).andReturn());
        assertThat(c.path("session").path("statut").asString()).isEqualTo("CLOTUREE");
        assertThat(c.path("session").path("ecart").asLong()).isEqualTo(-500);

        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/encaissements")), encaissement("X", 1000, "ESPECES")))
                .andExpect(status().isConflict());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/cloture")), Map.of("montantCompte", 0)))
                .andExpect(status().isConflict());
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions")), Map.of("perimetre", "SKY", "fondOuverture", 20000)))
                .andExpect(status().isConflict());
        // Après la clôture : pièces figées (pas de retrait), bordereau de remise encore possible jusqu'au contrôle
        mvc.perform(en(CAISSE_SKY, delete("/api/v1/caisse/justificatifs/" + piece))).andExpect(status().isConflict());
        mvc.perform(en(CAISSE_SKY, depot(session, "BORDEREAU_REMISE", PNG, "remise.png"))).andExpect(status().isCreated());
        // La caissière ne contrôle pas sa propre clôture ; la Manager (consultation caisse, indépendante) oui
        mvc.perform(jsonBody(en(CAISSE_SKY, post("/api/v1/caisse/sessions/" + session + "/controle")), Map.of("conforme", true)))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(MANAGER, post("/api/v1/caisse/sessions/" + session + "/controle")),
                        Map.of("conforme", false, "commentaire", "Manque de 500 FCFA expliqué : monnaie rendue")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.controlePar").value("Manager des salons"))
                .andExpect(jsonPath("$.justificatifs").value(2));
        // Clôture contrôlée : la journée est figée
        mvc.perform(en(CAISSE_SKY, depot(session, "AUTRE", PNG, "tard.png"))).andExpect(status().isConflict());
    }
}
