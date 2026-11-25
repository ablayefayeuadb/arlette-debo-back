package sn.arlettedebo.platform.rh.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pointage par QR.
 *
 * @param secret clé HMAC-SHA256 de signature des QR de la borne : connue du serveur seul (jamais envoyée au navigateur)
 */
@Validated
@ConfigurationProperties(prefix = "app.pointage")
record PointageProperties(@NotBlank @Size(min = 32) String secret) {
}
