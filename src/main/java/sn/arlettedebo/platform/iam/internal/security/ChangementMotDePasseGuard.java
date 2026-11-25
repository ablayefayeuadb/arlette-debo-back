package sn.arlettedebo.platform.iam.internal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sn.arlettedebo.platform.shared.error.GlobalExceptionHandler;

/**
 * Mot de passe provisoire (compte créé ou réinitialisé par la Direction) : tant qu'il n'a pas été changé,
 * seules les routes d'authentification restent accessibles (profil, changement de mot de passe, déconnexion).
 * Appliqué côté serveur : masquer les écrans ne suffit pas.
 */
class ChangementMotDePasseGuard extends OncePerRequestFilter {

    static final String CODE = "MOT_DE_PASSE_A_CHANGER";
    private static final String AUTORISE = "/api/v1/auth/";

    private final ProblemSecurityHandlers handlers;

    ChangementMotDePasseGuard(ProblemSecurityHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(AUTORISE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PlatformAuthentication p) {
            var principal = p.getPrincipal();
            if (principal != null && principal.doitChangerMotDePasse()) {
                handlers.ecrire(response, GlobalExceptionHandler.probleme(HttpStatus.FORBIDDEN, CODE,
                        "Mot de passe provisoire : choisissez un nouveau mot de passe pour continuer."));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
