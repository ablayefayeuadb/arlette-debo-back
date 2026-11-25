package sn.arlettedebo.platform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base des tests d'API : vraie base PostgreSQL, vraie chaîne de sécurité, vrais JWT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
public abstract class ApiTest {

    public static final String MDP = "Arlette@2026";
    public static final String DG = "dg@yopmail.com";
    public static final String DO = "do@yopmail.com";
    public static final String MANAGER = "manager@yopmail.com";
    public static final String COORDINATRICE = "coordination@yopmail.com";
    public static final String CAISSE_SKY = "caissier@yopmail.com";
    public static final String CLIENTELE_KIDS = "clientele@yopmail.com";
    public static final String COIFFEUSE = "coiffeur@yopmail.com";
    public static final String BOUTIQUE = "boutique@yopmail.com";
    public static final String INVENTAIRES = "inventaire@yopmail.com";
    public static final String COMMUNITY = "community@yopmail.com";
    public static final String ADMINISTRATION = "administration@yopmail.com";

    private static final Map<String, String> JETONS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** Connexion réelle (mise en cache par e-mail pour accélérer la suite). */
    protected String jeton(String email) {
        return JETONS.computeIfAbsent(email, e -> connexion(e).path("accessToken").asString());
    }

    protected void oublierJeton(String email) {
        JETONS.remove(email);
    }

    protected JsonNode connexion(String email) {
        try {
            MvcResult r = mvc.perform(post("/api/v1/auth/connexion").contentType(MediaType.APPLICATION_JSON).header("X-Client", "native")
                    .content(json.writeValueAsString(Map.of("email", email, "motDePasse", MDP))))
                    .andExpect(status().isOk()).andReturn();
            return json.readTree(r.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Requête authentifiée (JSON ou multipart : Spring 7 n'a plus de type commun en dehors de la classe abstraite). */
    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B en(String email, B req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton(email));
    }

    protected MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode lire(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
