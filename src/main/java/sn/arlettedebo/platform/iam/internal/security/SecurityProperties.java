package sn.arlettedebo.platform.iam.internal.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de sécurité (préfixe {@code app.security}).
 * Validée au démarrage : l'application refuse de démarrer avec une configuration incomplète.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Login login,
        @NotNull List<String> corsAllowedOrigins,
        @Valid @NotNull Cookie refreshCookie,
        boolean exposeApiDocs) {

    /**
     * @param secret clé HMAC encodée en Base64 (≥ 256 bits) — fournie par variable d'environnement en production
     */
    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotBlank String secret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    /** Protection contre la force brute : verrouillage de compte + limitation par adresse IP. */
    public record Login(
            @Min(3) int maxAttempts,
            @NotNull Duration lockDuration,
            @Min(5) int maxPerIpPerMinute) {
    }

    public record Cookie(@NotBlank String name, @NotBlank String path, boolean secure, @NotBlank String sameSite) {
    }
}
