package sn.arlettedebo.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import sn.arlettedebo.platform.support.ApiTest;

@DisplayName("Sécurité : authentification, JWT, droits de la matrice, révocation")
class SecuriteTests extends ApiTest {

    // ------------------------------------------------------------------ Seeder & matrice

    @ParameterizedTest(name = "{0} → profil {1}")
    @CsvSource({
        "dg@yopmail.com, DG",
        "do@yopmail.com, DO",
        "manager@yopmail.com, MANAGER",
        "coordination@yopmail.com, COORDINATION",
        "caissier@yopmail.com, CAISSIER",
        "clientele@yopmail.com, CHARGE_CLIENTELE",
        "coiffeur@yopmail.com, COIFFEUR",
        "boutique@yopmail.com, VENDEUR_BOUTIQUE",
        "inventaire@yopmail.com, INVENTAIRE",
        "community@yopmail.com, COMMUNITY_MANAGER",
        "administration@yopmail.com, ADMINISTRATION",
    })
    @DisplayName("Le seeder crée un utilisateur par profil, chacun peut se connecter")
    void unUtilisateurParProfil(String email, String profil) throws Exception {
        mvc.perform(en(email, get("/api/v1/auth/moi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value(profil))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("Droits effectifs = matrice (implications C et G⇒S incluses)")
    void droitsConformesALaMatrice() throws Exception {
        JsonNode dg = lire(mvc.perform(en(DG, get("/api/v1/auth/moi"))).andReturn());
        assertThat(actions(dg, "administration")).containsExactly("C", "V");
        assertThat(actions(dg, "rh")).containsExactly("C");
        assertThat(actions(dg, "dashboard")).containsExactly("C", "S", "G", "V");

        JsonNode admin = lire(mvc.perform(en(ADMINISTRATION, get("/api/v1/auth/moi"))).andReturn());
        assertThat(actions(admin, "administration")).containsExactly("C", "S", "G");
        assertThat(actions(admin, "rh")).containsExactly("C", "S", "G");
        assertThat(admin.path("droits").has("planning")).isFalse();
        assertThat(admin.path("restrictions").path("dashboard").asString()).isEqualTo("administratif");

        JsonNode coiffeuse = lire(mvc.perform(en(COIFFEUSE, get("/api/v1/auth/moi"))).andReturn());
        assertThat(coiffeuse.path("restrictions").path("planning").asString()).isEqualTo("personnel");
        assertThat(coiffeuse.path("perimetres").toString()).contains("SKY");

        JsonNode inventaires = lire(mvc.perform(en(INVENTAIRES, get("/api/v1/auth/moi"))).andReturn());
        assertThat(inventaires.path("modules")).hasToString("[\"stocks\"]");
    }

    @Test
    @DisplayName("Matrice exposée : 11 profils, cellules actives (sans communication)")
    void matrice() throws Exception {
        mvc.perform(en(DG, get("/api/v1/iam/matrice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profils.length()").value(11))
                .andExpect(jsonPath("$.modules.length()").value(8))
                .andExpect(jsonPath("$.cellules.length()").value(45));
    }

    // ------------------------------------------------------------------ Authentification

    @Test
    @DisplayName("Sans jeton : 401 Problem Details")
    void sansJeton() throws Exception {
        mvc.perform(get("/api/v1/auth/moi"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
    }

    @Test
    @DisplayName("JWT signé avec une autre clé : refusé")
    void jetonFalsifie() throws Exception {
        var autreCle = new SecretKeySpec("une-cle-pirate-de-256-bits-minimum-!!!!".getBytes(), "HmacSHA256");
        String faux = new NimbusJwtEncoder(new ImmutableSecret<>(autreCle)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("arlette-debo-platform").audience(List.of("arlette-debo-front"))
                        .subject(UUID.randomUUID().toString()).expiresAt(Instant.now().plusSeconds(600)).claim("ver", 0).build()))
                .getTokenValue();
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, "Bearer " + faux)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mauvais mot de passe : 401 générique ; e-mail inconnu : même réponse")
    void identifiantsInvalides() throws Exception {
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", DO, "motDePasse", "Mauvais@2026")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "inconnu@arlettedebo.sn", "motDePasse", "Mauvais@2026")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTIFIANTS_INVALIDES"));
    }

    @Test
    @DisplayName("5 échecs : compte verrouillé (423), même avec le bon mot de passe ; la DG déverrouille")
    void verrouillage() throws Exception {
        JsonNode cree = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")),
                Map.of("nom", "Test Verrou", "email", "verrou@arlettedebo.sn", "profil", "COIFFEUR", "motDePasse", MDP, "telephone", "77 100 00 01")))
                .andExpect(status().isCreated()).andReturn());
        for (int i = 0; i < 5; i++) {
            mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "verrou@arlettedebo.sn", "motDePasse", "Faux@20260")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "verrou@arlettedebo.sn", "motDePasse", MDP)))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("COMPTE_VERROUILLE"));
        mvc.perform(en(DG, post("/api/v1/iam/utilisateurs/" + cree.path("compte").path("id").asString() + "/deverrouillage")))
                .andExpect(status().isOk());
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "verrou@arlettedebo.sn", "motDePasse", MDP)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Jetons

    @Test
    @DisplayName("Rafraîchissement : rotation via cookie HttpOnly ; le cookie n'est pas lisible dans le corps")
    void rotationParCookie() throws Exception {
        MvcResult login = mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", MANAGER, "motDePasse", MDP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("ad_refresh=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");
        String valeur = setCookie.substring("ad_refresh=".length(), setCookie.indexOf(';'));
        // Requête forgée par un site tiers : le cookie part, mais pas l'en-tête personnalisé → refusée (CSRF)
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(new Cookie("ad_refresh", valeur)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF"));
        mvc.perform(post("/api/v1/auth/deconnexion").cookie(new Cookie("ad_refresh", valeur)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF"));
        // Requête de l'application (en-tête X-Requested-With) : acceptée, et le jeton n'a pas été consommé avant
        mvc.perform(post("/api/v1/auth/rafraichir").cookie(new Cookie("ad_refresh", valeur)).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Rejeu d'un refresh déjà utilisé : famille révoquée ET jetons d'accès invalidés")
    void detectionDeRejeu() throws Exception {
        JsonNode s1 = connexion(BOUTIQUE);
        String refresh1 = s1.path("refreshToken").asString();
        JsonNode s2 = lire(mvc.perform(jsonBody(post("/api/v1/auth/rafraichir").header("X-Client", "native"), Map.of("refreshToken", refresh1)))
                .andExpect(status().isOk()).andReturn());
        String acces2 = s2.path("accessToken").asString();
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces2)).andExpect(status().isOk());

        // Un attaquant rejoue l'ancien jeton
        mvc.perform(jsonBody(post("/api/v1/auth/rafraichir"), Map.of("refreshToken", refresh1)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALIDE"));
        // La session légitime est aussi coupée (vol présumé)
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces2)).andExpect(status().isUnauthorized());
        mvc.perform(jsonBody(post("/api/v1/auth/rafraichir"), Map.of("refreshToken", s2.path("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
        oublierJeton(BOUTIQUE);
    }

    @Test
    @DisplayName("Déconnexion globale : le JWT encore valide est immédiatement refusé")
    void deconnexionGlobale() throws Exception {
        String acces = connexion(COMMUNITY).path("accessToken").asString();
        mvc.perform(post("/api/v1/auth/deconnexion-globale").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, "Bearer " + acces)).andExpect(status().isUnauthorized());
        oublierJeton(COMMUNITY);
    }

    // ------------------------------------------------------------------ Autorisations

    @Test
    @DisplayName("Accès refusés selon la matrice (403)")
    void accesRefuses() throws Exception {
        mvc.perform(en(COIFFEUSE, get("/api/v1/rh/employes"))).andExpect(status().isForbidden());
        mvc.perform(en(COIFFEUSE, get("/api/v1/iam/matrice"))).andExpect(status().isForbidden());
        mvc.perform(en(COIFFEUSE, get("/api/v1/finance/depenses"))).andExpect(status().isForbidden());
        mvc.perform(en(ADMINISTRATION, get("/api/v1/journal"))).andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DO, post("/api/v1/referentiels/salons")), Map.of("libelle", "Salon DO", "type", "SALON")))
                .andExpect(status().isForbidden());
        mvc.perform(en(INVENTAIRES, get("/api/v1/finance/synthese"))).andExpect(status().isForbidden());
        mvc.perform(en(DG, get("/api/v1/journal"))).andExpect(status().isOk());
        mvc.perform(en(MANAGER, get("/api/v1/journal"))).andExpect(status().isOk());
        mvc.perform(en(MANAGER, get("/api/v1/finance/depenses"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Habilitation « * » suspendue par la DG : effet immédiat sur un JWT déjà émis")
    void suspensionImmediate() throws Exception {
        mvc.perform(en(ADMINISTRATION, get("/api/v1/finance/depenses"))).andExpect(status().isOk());
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/habilitations/ADMINISTRATION/administration")), Map.of("accordee", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accordee").value(false));
        try {
            mvc.perform(en(ADMINISTRATION, get("/api/v1/finance/depenses"))).andExpect(status().isForbidden());
        } finally {
            mvc.perform(jsonBody(en(DG, put("/api/v1/iam/habilitations/ADMINISTRATION/administration")), Map.of("accordee", true)))
                    .andExpect(status().isOk());
        }
        mvc.perform(en(ADMINISTRATION, get("/api/v1/finance/depenses"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Seuls les accès « * » sont suspendables ; seuls DG et Manager les pilotent")
    void habilitationsReserveesDg() throws Exception {
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/habilitations/DG/caisse")), Map.of("accordee", false)))
                .andExpect(status().isConflict());
        mvc.perform(jsonBody(en(DO, put("/api/v1/iam/habilitations/ADMINISTRATION/administration")), Map.of("accordee", false)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESERVE_DG"));
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/iam/habilitations/ADMINISTRATION/administration")), Map.of("accordee", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accordee").value(false));
        mvc.perform(jsonBody(en(MANAGER, put("/api/v1/iam/habilitations/ADMINISTRATION/administration")), Map.of("accordee", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accordee").value(true));
    }

    @Test
    @DisplayName("Relais : activation refusée sans formation validée")
    void relaisSansFormation() throws Exception {
        JsonNode delegations = lire(mvc.perform(en(DG, get("/api/v1/iam/delegations"))).andReturn());
        String relaisSky = null;
        for (JsonNode d : delegations) {
            if ("RELAIS_SKY".equals(d.path("code").asString())) {
                relaisSky = d.path("id").asString();
            }
        }
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/delegations/" + relaisSky + "/activation")), Map.of("valeur", true)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORMATION_REQUISE"));
    }

    @Test
    @DisplayName("Comptes : la RH crée une Coiffeuse, pas un autre profil ; personne ne se désactive soi-même")
    void attributionDesProfils() throws Exception {
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/iam/utilisateurs")),
                Map.of("nom", "Nouvelle Manager", "email", "nm@arlettedebo.sn", "profil", "MANAGER", "telephone", "77 100 00 01")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ATTRIBUTION_PROFIL_DG"));
        mvc.perform(jsonBody(en(ADMINISTRATION, post("/api/v1/iam/utilisateurs")),
                Map.of("nom", "Nouvelle Coiffeuse", "email", "nc@arlettedebo.sn", "profil", "COIFFEUR", "perimetres", List.of("KIDS"), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motDePasseProvisoire").isNotEmpty())
                .andExpect(jsonPath("$.compte.doitChangerMotDePasse").value(true));
        String moi = lire(mvc.perform(en(DG, get("/api/v1/auth/moi"))).andReturn()).path("id").asString();
        mvc.perform(en(DG, post("/api/v1/iam/utilisateurs/" + moi + "/desactivation")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTO_DESACTIVATION"));
    }

    @Test
    @DisplayName("Mot de passe provisoire : API bloquée jusqu'au changement ; le changement révoque les anciennes sessions")
    void motDePasseProvisoireObligatoire() throws Exception {
        String provisoire = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")),
                        Map.of("nom", "Première Connexion", "email", "premiere@arlettedebo.sn", "profil", "COIFFEUR", "perimetres", List.of("SKY"), "telephone", "77 100 00 01")))
                .andExpect(status().isCreated()).andReturn()).path("motDePasseProvisoire").asString();
        JsonNode s1 = lire(mvc.perform(jsonBody(post("/api/v1/auth/connexion").header("X-Client", "native"),
                        Map.of("email", "premiere@arlettedebo.sn", "motDePasse", provisoire)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(true)).andReturn());
        String acces1 = "Bearer " + s1.path("accessToken").asString();
        String refresh1 = s1.path("refreshToken").asString();

        // Tout est refusé sauf les routes d'authentification
        mvc.perform(get("/api/v1/pilotage/a-traiter").header(HttpHeaders.AUTHORIZATION, acces1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, acces1)).andExpect(status().isOk());

        // Première connexion : pas besoin de l'ancien MDP ; politique appliquée sur le nouveau
        mvc.perform(jsonBody(post("/api/v1/auth/mot-de-passe").header(HttpHeaders.AUTHORIZATION, acces1),
                        Map.of("nouveau", "sansmajuscule2026"))).andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(post("/api/v1/auth/mot-de-passe").header(HttpHeaders.AUTHORIZATION, acces1),
                        Map.of("nouveau", "Premiere2026x"))).andExpect(status().isUnprocessableContent());

        // Changement sans « actuel » : nouvelle session pour cet appareil, cookie renouvelé
        MvcResult change = mvc.perform(jsonBody(post("/api/v1/auth/mot-de-passe").header(HttpHeaders.AUTHORIZATION, acces1),
                        Map.of("nouveau", "Coiffure@Sky2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(false)).andReturn();
        assertThat(change.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith("ad_refresh=").contains("HttpOnly");
        String acces2 = "Bearer " + lire(change).path("accessToken").asString();
        mvc.perform(get("/api/v1/pilotage/a-traiter").header(HttpHeaders.AUTHORIZATION, acces2)).andExpect(status().isOk());

        // Anciennes sessions révoquées ; ancien mot de passe refusé, nouveau accepté
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, acces1)).andExpect(status().isUnauthorized());
        mvc.perform(jsonBody(post("/api/v1/auth/rafraichir"), Map.of("refreshToken", refresh1))).andExpect(status().isUnauthorized());
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "premiere@arlettedebo.sn", "motDePasse", provisoire)))
                .andExpect(status().isUnauthorized());
        mvc.perform(jsonBody(post("/api/v1/auth/connexion"), Map.of("email", "premiere@arlettedebo.sn", "motDePasse", "Coiffure@Sky2026")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.utilisateur.doitChangerMotDePasse").value(false));
    }

    @Test
    @DisplayName("Salons d'un compte : la DG les modifie, effet immédiat sur les droits ; codes vérifiés")
    void perimetresDuCompte() throws Exception {
        String id = lire(mvc.perform(jsonBody(en(DG, post("/api/v1/iam/utilisateurs")),
                        Map.of("nom", "Coiffeuse Mobile", "email", "mobile@arlettedebo.sn", "profil", "COIFFEUR",
                                "perimetres", List.of("SKY"), "motDePasse", "Coupe@Sky2026x", "telephone", "77 100 00 01")))
                .andExpect(status().isCreated()).andReturn()).path("compte").path("id").asString();
        String jeton = "Bearer " + lire(mvc.perform(jsonBody(post("/api/v1/auth/connexion"),
                Map.of("email", "mobile@arlettedebo.sn", "motDePasse", "Coupe@Sky2026x"))).andReturn()).path("accessToken").asString();
        // (mot de passe choisi par la DG : provisoire, à changer — on lit seulement /auth/moi, autorisé)
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, jeton)).andExpect(jsonPath("$.perimetres[0]").value("SKY"));

        mvc.perform(jsonBody(en(ADMINISTRATION, put("/api/v1/iam/utilisateurs/" + id + "/perimetres")), Map.of("perimetres", List.of("KIDS"))))
                .andExpect(status().isForbidden());
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/utilisateurs/" + id + "/perimetres")), Map.of("perimetres", List.of("LUNE"))))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/utilisateurs/" + id + "/perimetres")), Map.of("perimetres", List.of("SKY", "KIDS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perimetres.length()").value(2));
        // Sans reconnexion : le même jeton porte désormais les deux salons
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, jeton)).andExpect(jsonPath("$.perimetres.length()").value(2));
        // Liste vide : retour aux salons du profil
        mvc.perform(jsonBody(en(DG, put("/api/v1/iam/utilisateurs/" + id + "/perimetres")), Map.of("perimetres", List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perimetres.length()").value(0));
        mvc.perform(get("/api/v1/auth/moi").header(HttpHeaders.AUTHORIZATION, jeton)).andExpect(jsonPath("$.perimetres[0]").value("SKY"));
    }

    @Test
    @DisplayName("Journal d'audit : connexions et actions sensibles tracées")
    void journal() throws Exception {
        JsonNode page = lire(mvc.perform(en(DG, get("/api/v1/journal").param("taille", "200"))).andExpect(status().isOk()).andReturn());
        List<String> actions = new ArrayList<>();
        page.path("contenu").forEach(e -> actions.add(e.path("action").asString()));
        assertThat(actions).contains("Connexion");
    }

    private static List<String> actions(JsonNode moi, String module) {
        List<String> out = new ArrayList<>();
        moi.path("droits").path(module).forEach(a -> out.add(a.asString()));
        return out;
    }
}
