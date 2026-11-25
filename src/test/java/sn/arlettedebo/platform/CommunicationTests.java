package sn.arlettedebo.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.arlettedebo.platform.support.ApiTest;

/** Module Communication retiré : aucun profil (y compris Community / DO / DG) n'y a accès. */
@DisplayName("Communication : module retiré")
class CommunicationTests extends ApiTest {

    @Test
    @DisplayName("Calendrier et création refusés pour tous les profils concernés")
    void moduleRetire() throws Exception {
        String du = LocalDate.now().minusDays(30).toString();
        String au = LocalDate.now().plusDays(60).toString();
        Map<String, Object> corps = Map.of("titre", "X", "contenu", "Y", "format", "POST", "canal", "INSTAGRAM",
                "datePrevue", LocalDate.now().plusDays(5).toString());
        for (String compte : new String[] {COMMUNITY, DO, DG, MANAGER}) {
            mvc.perform(en(compte, get("/api/v1/communication/publications").param("du", du).param("au", au)))
                    .andExpect(status().isForbidden());
            mvc.perform(jsonBody(en(compte, post("/api/v1/communication/publications")), corps)).andExpect(status().isForbidden());
        }
    }
}
