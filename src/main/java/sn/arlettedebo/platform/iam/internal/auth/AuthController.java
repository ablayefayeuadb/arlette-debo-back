package sn.arlettedebo.platform.iam.internal.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.internal.auth.AuthService.Session;
import sn.arlettedebo.platform.iam.internal.security.SecurityProperties;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Authentification.
 * <p>Le jeton d'accès (JWT) est renvoyé dans le corps ; le jeton de rafraîchissement est posé dans un
 * cookie {@code HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth} — inaccessible au JavaScript (XSS).
 * Les clients non navigateurs peuvent le transmettre dans le corps.
 */
@Tag(name = "Authentification")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {


    private final AuthService auth;
    private final SecurityProperties props;

    @Operation(summary = "Connexion par e-mail et mot de passe")
    @PostMapping("/connexion")
    ResponseEntity<SessionResponse> connexion(@Valid @RequestBody ConnexionRequest req, HttpServletRequest http) {
        Session s = auth.connecter(req.email(), req.motDePasse(), http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
        return reponse(s, http);
    }

    @Operation(summary = "Renouveler le jeton d'accès (rotation du jeton de rafraîchissement)")
    @PostMapping("/rafraichir")
    ResponseEntity<SessionResponse> rafraichir(@CookieValue(name = "${app.security.refresh-cookie.name}", required = false) String cookie,
            @RequestBody(required = false) RafraichirRequest body, HttpServletRequest http) {
        Session s = auth.rafraichir(jetonFourni(cookie, body), http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
        return reponse(s, http);
    }

    @Operation(summary = "Se déconnecter (révoque la session de l'appareil)")
    @PostMapping("/deconnexion")
    ResponseEntity<Void> deconnexion(@CookieValue(name = "${app.security.refresh-cookie.name}", required = false) String cookie,
            @RequestBody(required = false) RafraichirRequest body) {
        auth.deconnecter(jetonFourni(cookie, body));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie(null, Duration.ZERO).toString()).build();
    }

    @Operation(summary = "Se déconnecter de tous les appareils")
    @PostMapping("/deconnexion-globale")
    ResponseEntity<Void> deconnexionGlobale(@AuthenticationPrincipal CurrentUser moi) {
        auth.deconnecterPartout(moi.id());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie(null, Duration.ZERO).toString()).build();
    }

    @Operation(summary = "Profil, périmètres et droits effectifs de l'utilisateur connecté")
    @GetMapping("/moi")
    MoiResponse moi(@AuthenticationPrincipal CurrentUser moi) {
        return MoiResponse.of(moi);
    }

    @Operation(summary = "Changer son mot de passe (révoque toutes les sessions et en rouvre une pour cet appareil)")
    @PostMapping("/mot-de-passe")
    ResponseEntity<SessionResponse> motDePasse(@AuthenticationPrincipal CurrentUser moi,
            @Valid @RequestBody ChangementMotDePasseRequest req, HttpServletRequest http) {
        Session s = auth.changerMotDePasse(moi.id(), req.actuel(), req.nouveau(), http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT));
        return reponse(s, http);
    }

    /** Le jeton de rafraîchissement n'apparaît dans le corps que pour un client natif ({@code X-Client: native}). */
    private ResponseEntity<SessionResponse> reponse(Session s, HttpServletRequest http) {
        boolean natif = "native".equalsIgnoreCase(http.getHeader("X-Client"));
        Duration ttl = Duration.between(Instant.now(), s.rafraichissement().expireLe());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.rafraichissement().valeur(), ttl).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SessionResponse(s.acces().valeur(), "Bearer", s.acces().expireDansSecondes(),
                        natif ? s.rafraichissement().valeur() : null, MoiResponse.of(s.utilisateur())));
    }

    /** Navigateur : cookie HttpOnly ; client natif : corps de la requête. */
    private static String jetonFourni(String cookie, RafraichirRequest body) {
        if (cookie != null) {
            return cookie;
        }
        return body == null ? null : body.refreshToken();
    }

    private ResponseCookie cookie(String valeur, Duration ttl) {
        return ResponseCookie.from(props.refreshCookie().name(), valeur == null ? "" : valeur)
                .httpOnly(true)
                .secure(props.refreshCookie().secure())
                .sameSite(props.refreshCookie().sameSite())
                .path(props.refreshCookie().path())
                .maxAge(ttl)
                .build();
    }

    record ConnexionRequest(@NotBlank @Email @Size(max = 160) String email, @NotBlank @Size(max = 72) String motDePasse) {
    }

    record RafraichirRequest(String refreshToken) {
    }

    /** {@code actuel} requis sauf à la première connexion (mot de passe provisoire). */
    record ChangementMotDePasseRequest(@Size(max = 72) String actuel, @NotBlank @Size(min = 10, max = 72) String nouveau) {
    }

    record SessionResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, MoiResponse utilisateur) {
    }

    /**
     * @param droits      actions effectives par module, implications comprises (C, S, G, V)
     * @param modules     modules visibles (au moins C)
     */
    record MoiResponse(UUID id, String nom, String email, String profil, Set<String> perimetres,
            Map<String, List<String>> droits, Map<String, String> restrictions, List<String> modules,
            List<String> delegations, boolean doitChangerMotDePasse) {

        static MoiResponse of(CurrentUser u) {
            Map<String, List<String>> droits = new java.util.LinkedHashMap<>();
            for (ModuleCode m : ModuleCode.values()) {
                List<String> a = java.util.Arrays.stream(ActionCode.values()).filter(x -> u.peut(m, x)).map(Enum::name).toList();
                if (!a.isEmpty()) {
                    droits.put(m.code(), a);
                }
            }
            Map<String, String> restrictions = new java.util.LinkedHashMap<>();
            u.restrictions().forEach((k, v) -> restrictions.put(k.code(), v));
            return new MoiResponse(u.id(), u.nom(), u.email(), u.profil(), u.perimetres(), droits, restrictions,
                    List.copyOf(droits.keySet()), u.delegations(), u.doitChangerMotDePasse());
        }
    }
}
