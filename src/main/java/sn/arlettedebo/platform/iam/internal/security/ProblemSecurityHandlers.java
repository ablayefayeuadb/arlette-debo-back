package sn.arlettedebo.platform.iam.internal.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.shared.error.GlobalExceptionHandler;

/** Réponses 401 / 403 de la chaîne de filtres au même format Problem Details que l'API. */
@Component
@RequiredArgsConstructor
class ProblemSecurityHandlers {

    private final ObjectMapper mapper;

    AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            ecrire(response, GlobalExceptionHandler.probleme(HttpStatus.UNAUTHORIZED, "NON_AUTHENTIFIE",
                    "Authentification requise ou jeton invalide/expiré."));
        };
    }

    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> ecrire(response, GlobalExceptionHandler.probleme(HttpStatus.FORBIDDEN,
                "ACCES_REFUSE", "Votre profil n'est pas habilité pour cette action."));
    }

    void ecrire(HttpServletResponse response, ProblemDetail pd) throws IOException {
        response.setStatus(pd.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
