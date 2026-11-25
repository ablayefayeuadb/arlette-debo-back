package sn.arlettedebo.platform.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Documentation OpenAPI / Swagger UI — active uniquement si {@code app.security.expose-api-docs=true}
 * (profil {@code dev}, ou staging via {@code EXPOSE_API_DOCS=true}).
 *
 * <ul>
 *   <li>Deux schémas de sécurité : jeton Bearer collé à la main, ou connexion e-mail / mot de passe
 *       (flux OAuth2 « password » vers {@code /api/v1/auth/swagger/jeton}).</li>
 *   <li>Routes publiques (connexion, rafraîchissement, déconnexion) sans cadenas.</li>
 *   <li>Réponses d'erreur communes au format Problem Details (RFC 9457) avec le code métier.</li>
 *   <li>Un groupe par module fonctionnel (liste déroulante en haut à droite de l'UI).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.security.expose-api-docs", havingValue = "true")
class OpenApiConfig {

    static final String BEARER = "bearer";
    static final String CONNEXION = "connexion";
    private static final String PROBLEME = "Probleme";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Set<String> PUBLIQUES = Set.of(
            "/api/v1/auth/connexion", "/api/v1/auth/rafraichir", "/api/v1/auth/deconnexion");

    @Value("${app.security.public-api-url:}")
    private String publicApiUrl;

    @Bean
    OpenAPI plateformeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Arlette Debo — API de la plateforme")
                        .version("v1")
                        .contact(new Contact().name("Arlette Debo — équipe technique"))
                        .description("""
                                API du monolithe modulaire (SKY · KIDS · Boutique).

                                **S'authentifier dans cette page** : bouton **Authorize** →
                                *connexion* : e-mail + mot de passe d'un compte (dev : `dg@yopmail.com` / `Arlette@2026`),
                                ou *bearer* : coller un `accessToken` obtenu via `POST /api/v1/auth/connexion`.
                                Le jeton d'accès dure 15 minutes.

                                **Droits** : matrice des profils & fonctionnalités — C consultation, S saisie, G gestion,
                                V validation — limitée au périmètre du compte. Les droits effectifs de l'utilisateur
                                connecté sont donnés par `GET /api/v1/auth/moi`.

                                **Erreurs** : Problem Details (RFC 9457), avec une propriété `code` stable
                                (ex. `IDENTIFIANTS_INVALIDES`, `ACCES_REFUSE`, `SEPARATION_DES_TACHES`).

                                **Montants** : entiers en FCFA. **Dates** : ISO 8601, fuseau Africa/Dakar."""))
                .servers(serveurs())
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme(BEARER).bearerFormat("JWT")
                                .description("Jeton d'accès renvoyé par POST /api/v1/auth/connexion (champ accessToken)."))
                        .addSecuritySchemes(CONNEXION, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Connexion par e-mail et mot de passe (laisser client_id / client_secret vides).")
                                .flows(new OAuthFlows().password(new OAuthFlow()
                                        .tokenUrl("/api/v1/auth/swagger/jeton")
                                        .scopes(new Scopes()))))
                        .addSchemas(PROBLEME, probleme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .addSecurityItem(new SecurityRequirement().addList(CONNEXION));
    }

    private List<Server> serveurs() {
        List<Server> out = new ArrayList<>();
        if (StringUtils.hasText(publicApiUrl)) {
            String url = publicApiUrl.endsWith("/") ? publicApiUrl.substring(0, publicApiUrl.length() - 1) : publicApiUrl;
            out.add(new Server().url(url).description("API publique"));
        }
        out.add(new Server().url("/").description("Serveur courant"));
        return out;
    }

    /** Sécurité retirée des routes publiques et réponses d'erreur communes ajoutées partout (appliqué à chaque groupe). */
    private static OpenApiCustomizer reponsesCommunes() {
        return api -> api.getPaths().forEach((chemin, item) ->
                item.readOperationsMap().forEach((methode, op) -> documenter(chemin, methode, op)));
    }

    private static void documenter(String chemin, PathItem.HttpMethod methode, Operation op) {
        boolean publique = PUBLIQUES.contains(chemin);
        ApiResponses r = op.getResponses();
        if (publique) {
            op.setSecurity(List.of());
        } else {
            ajouter(r, "401", "Non authentifié ou jeton expiré");
            ajouter(r, "403", "Droit insuffisant pour ce profil ou ce périmètre");
        }
        boolean parametres = op.getParameters() != null && !op.getParameters().isEmpty();
        if (op.getRequestBody() != null || parametres) {
            ajouter(r, "400", "Requête invalide (validation, règle métier)");
        }
        if (methode != PathItem.HttpMethod.GET && chemin.contains("{")) {
            ajouter(r, "404", "Ressource introuvable");
            ajouter(r, "409", "Conflit : état incompatible ou modification concurrente");
        }
    }

    // ------------------------------------------------------------ Groupes

    @Bean
    GroupedOpenApi groupeTout() {
        return groupe("0-tout", "0 · Toute l'API", "/api/v1/**");
    }

    @Bean
    GroupedOpenApi groupeAcces() {
        return groupe("1-acces", "1 · Authentification, habilitations, journal", "/api/v1/auth/**", "/api/v1/iam/**", "/api/v1/journal/**");
    }

    @Bean
    GroupedOpenApi groupePilotage() {
        return groupe("2-pilotage", "2 · Pilotage (DG / DO)", "/api/v1/pilotage/**");
    }

    @Bean
    GroupedOpenApi groupeOperations() {
        return groupe("3-operations", "3 · Opérations salons : planning, qualité, caisse, commercial",
                "/api/v1/planning/**", "/api/v1/qualite/**", "/api/v1/caisse/**", "/api/v1/commercial/**");
    }

    @Bean
    GroupedOpenApi groupeSupport() {
        return groupe("4-support", "4 · Fonctions support : RH, stocks, communication, finance, référentiels",
                "/api/v1/rh/**", "/api/v1/stock/**", "/api/v1/communication/**", "/api/v1/finance/**",
                "/api/v1/referentiels/**", "/api/v1/gouvernance/**");
    }

    private static GroupedOpenApi groupe(String nom, String libelle, String... chemins) {
        return GroupedOpenApi.builder()
                .group(nom)
                .displayName(libelle)
                .pathsToMatch(chemins)
                .addOpenApiCustomizer(reponsesCommunes())
                .build();
    }

    // ------------------------------------------------------------ Schémas

    @SuppressWarnings("rawtypes")
    private static Schema<?> probleme() {
        Map<String, Schema> props = new java.util.LinkedHashMap<>();
        props.put("type", new StringSchema().example("https://arlettedebo.sn/problemes/acces-refuse"));
        props.put("title", new StringSchema().example("Forbidden"));
        props.put("status", new IntegerSchema().example(403));
        props.put("detail", new StringSchema().example("Action non autorisée pour votre profil."));
        props.put("instance", new StringSchema().example("/api/v1/finance/depenses/…/validation"));
        props.put("code", new StringSchema().example("ACCES_REFUSE").description("Code métier stable, exploitable par les clients"));
        props.put("erreurs", new MapSchema().additionalProperties(new StringSchema())
                .description("Erreurs de validation par champ (400)"));
        return new ObjectSchema().description("Problem Details (RFC 9457)").properties(props);
    }

    private static void ajouter(ApiResponses r, String statut, String description) {
        if (r.containsKey(statut)) {
            return;
        }
        r.addApiResponse(statut, new ApiResponse().description(description).content(new Content()
                .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEME)))));
    }
}
