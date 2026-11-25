package sn.arlettedebo.platform.iam.internal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.arlettedebo.platform.shared.error.GlobalExceptionHandler;

/**
 * Protection CSRF des seules routes authentifiées par cookie (rafraîchissement, déconnexion).
 * <p>Le reste de l'API est authentifié par l'en-tête {@code Authorization: Bearer}, qu'un site tiers ne peut
 * pas faire envoyer par le navigateur : la protection CSRF de Spring y est donc inutile (désactivée).
 * Pour les routes à cookie, en plus de {@code SameSite=Strict}, on exige l'en-tête personnalisé
 * {@code X-Requested-With} : un en-tête non standard déclenche un pré-vol CORS, refusé pour toute
 * origine non autorisée — une page malveillante ne peut donc pas forger la requête.
 */
class CookieCsrfGuard extends OncePerRequestFilter {

    static final String ENTETE = "X-Requested-With";
    private static final Set<String> ROUTES_A_COOKIE = Set.of("/api/v1/auth/rafraichir", "/api/v1/auth/deconnexion");

    private final String nomCookie;
    private final ProblemSecurityHandlers handlers;

    CookieCsrfGuard(String nomCookie, ProblemSecurityHandlers handlers) {
        this.nomCookie = nomCookie;
        this.handlers = handlers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !ROUTES_A_COOKIE.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean avecCookie = request.getCookies() != null
                && Arrays.stream(request.getCookies()).map(Cookie::getName).anyMatch(nomCookie::equals);
        String entete = request.getHeader(ENTETE);
        if (avecCookie && (entete == null || entete.isBlank())) {
            handlers.ecrire(response, GlobalExceptionHandler.probleme(HttpStatus.FORBIDDEN, "CSRF",
                    "En-tête " + ENTETE + " requis pour une requête authentifiée par cookie."));
            return;
        }
        chain.doFilter(request, response);
    }
}
