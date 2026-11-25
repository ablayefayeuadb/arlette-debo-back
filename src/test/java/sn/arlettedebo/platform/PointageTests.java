package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import sn.arlettedebo.platform.rh.internal.JetonBorne;
import sn.arlettedebo.platform.support.ApiTest;
import tools.jackson.databind.JsonNode;

/**
 * Pointage par QR signé côté serveur : la borne affiche un QR unique pour tout le site,
 * tout employé(e) le scanne depuis son compte ; aucun pointage sans QR valide et frais.
 */
@DisplayName("Pointage QR : borne site, scan, sécurité du jeton, tableau des présences")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PointageTests extends ApiTest {

    /** Clé de test (application-test.yml) : permet de fabriquer un jeton expiré correctement signé. */
    private static final String CLE = "test-only-cle-pointage-qr-arlette-debo-0123456789";

    private String borne(String email) throws Exception {
        return lire(mvc.perform(en(email, get("/api/v1/rh/pointage/borne")))
                .andExpect(status().isOk()).andReturn()).path("jeton").asString();
    }

    private org.springframework.test.web.servlet.ResultActions scan(String email, String jeton, String action) throws Exception {
        return mvc.perform(jsonBody(en(email, post("/api/v1/rh/pointage/scan")), Map.of("jeton", jeton, "action", action)));
    }

    private static String signe(long fenetre) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CLE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] h = mac.doFinal(("AD1|" + JetonBorne.PORTEE + "|" + fenetre).getBytes(StandardCharsets.UTF_8));
        return "AD1." + JetonBorne.PORTEE + "." + fenetre + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(h, 16));
    }

    @Test
    @Order(1)
    @DisplayName("Borne : même QR site pour Manager et accueil ; jamais pour la coiffeuse qui pointe")
    void borne() throws Exception {
        String qrManager = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/pointage/borne")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rotationSecondes").value(60)).andExpect(jsonPath("$.expireLe").isNotEmpty())
                .andReturn()).path("jeton").asString();
        assertThat(qrManager).startsWith("AD1.SITE.");
        String qrCaisse = borne(CAISSE_SKY);
        assertThat(qrCaisse).isEqualTo(qrManager);
        mvc.perform(en(COIFFEUSE, get("/api/v1/rh/pointage/borne"))).andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    @DisplayName("Jetons refusés : falsifié, mal formé, expiré ; l'ancien pointage sans QR n'existe plus")
    void securite() throws Exception {
        String qr = borne(MANAGER);
        String falsifie = qr.substring(0, qr.lastIndexOf('.') + 1) + "AAAAAAAAAAAAAAAAAAAAAA";
        scan(COIFFEUSE, falsifie, "ARRIVEE").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("QR_POINTAGE"));
        scan(COIFFEUSE, "https://exemple.org/qr", "ARRIVEE").andExpect(status().isForbidden());
        long ancienne = System.currentTimeMillis() / 1000 / 60 - 5;
        scan(COIFFEUSE, signe(ancienne), "ARRIVEE").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expiré")));
        mvc.perform(en(COIFFEUSE, post("/api/v1/rh/pointages/moi/arrivee"))).andExpect(status().is4xxClientError());
        mvc.perform(en(COIFFEUSE, get("/api/v1/rh/pointage/moi"))).andExpect(jsonPath("$.statut").value("ABSENTE"));
    }

    @Test
    @Order(3)
    @DisplayName("Journée : arrivée, pause, reprise, départ — chaque action une seule fois, dans l'ordre")
    void journee() throws Exception {
        String qr = borne(MANAGER);
        scan(COIFFEUSE, qr, "PAUSE_DEBUT").andExpect(status().isConflict());
        scan(COIFFEUSE, qr, "ARRIVEE").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRESENTE"))
                .andExpect(jsonPath("$.actionsPossibles.length()").value(2));
        scan(COIFFEUSE, qr, "ARRIVEE").andExpect(status().isConflict());
        scan(COIFFEUSE, qr, "PAUSE_DEBUT").andExpect(jsonPath("$.statut").value("EN_PAUSE"));
        scan(COIFFEUSE, qr, "PAUSE_FIN").andExpect(jsonPath("$.statut").value("PRESENTE"))
                .andExpect(jsonPath("$.actionsPossibles[0]").value("DEPART"));
        scan(COIFFEUSE, qr, "DEPART").andExpect(jsonPath("$.statut").value("PARTIE")).andExpect(jsonPath("$.actionsPossibles.length()").value(0));
        scan(COIFFEUSE, qr, "DEPART").andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    @DisplayName("Tableau des présences : la Manager voit l'équipe ; l'accueil (sans RH) non")
    void tableau() throws Exception {
        String monNom = lire(mvc.perform(en(COIFFEUSE, get("/api/v1/rh/pointage/moi"))).andExpect(status().isOk()).andReturn())
                .path("nom").asString();
        JsonNode liste = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/pointage/equipe").param("jour", LocalDate.now().toString())))
                .andExpect(status().isOk()).andReturn());
        boolean partie = false;
        for (JsonNode p : liste) {
            if (monNom.equals(p.path("nom").asString()) && "PARTIE".equals(p.path("statut").asString())) {
                partie = true;
                break;
            }
        }
        assertThat(partie).as("coiffeuse pointée doit apparaître PARTIE").isTrue();
        mvc.perform(en(CAISSE_SKY, get("/api/v1/rh/pointage/equipe").param("jour", LocalDate.now().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Même QR site : un utilisateur d'un autre salon peut pointer avec le QR de la borne")
    void memeQrTousSalons() throws Exception {
        String qr = borne(MANAGER);
        scan(CAISSE_SKY, qr, "ARRIVEE").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRESENTE"));
    }

    @Test
    @Order(6)
    @DisplayName("Manager : apparaît sur le tableau après avoir pointé")
    void managerSurBorne() throws Exception {
        String qr = borne(MANAGER);
        scan(MANAGER, qr, "ARRIVEE").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRESENTE"));
        String monNom = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/pointage/moi"))).andExpect(status().isOk()).andReturn())
                .path("nom").asString();
        JsonNode liste = lire(mvc.perform(en(MANAGER, get("/api/v1/rh/pointage/equipe").param("jour", LocalDate.now().toString())))
                .andExpect(status().isOk()).andReturn());
        boolean vue = false;
        for (JsonNode p : liste) {
            if (monNom.equals(p.path("nom").asString()) && "PRESENTE".equals(p.path("statut").asString())) {
                vue = true;
                break;
            }
        }
        assertThat(vue).as("Manager doit figurer Présente").isTrue();
    }

}
