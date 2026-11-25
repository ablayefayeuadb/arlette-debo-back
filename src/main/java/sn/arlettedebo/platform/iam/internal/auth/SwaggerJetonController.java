package sn.arlettedebo.platform.iam.internal.auth;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.internal.auth.AuthService.Session;

/**
 * Point de jeton au format OAuth2 « password » pour le bouton <em>Authorize</em> de Swagger UI.
 * <p>N'existe que si la documentation est exposée (profil dev). Même chemin de connexion que
 * {@code /auth/connexion} : limitation par IP, verrouillage après échecs, journal d'audit.
 * Aucun jeton de rafraîchissement n'est remis (session Swagger de 15 minutes).
 */
@Hidden
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security.expose-api-docs", havingValue = "true")
class SwaggerJetonController {

    static final String CHEMIN = "/api/v1/auth/swagger/jeton";

    private final AuthService auth;

    @PostMapping(path = CHEMIN, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Map<String, Object>> jeton(@RequestParam("username") String email, @RequestParam("password") String motDePasse,
            HttpServletRequest http) {
        Session s = auth.connecter(email, motDePasse, http.getRemoteAddr(), "swagger-ui");
        auth.deconnecter(s.rafraichissement().valeur());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("access_token", s.acces().valeur(), "token_type", "Bearer", "expires_in", s.acces().expireDansSecondes()));
    }
}
