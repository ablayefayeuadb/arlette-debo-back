package sn.arlettedebo.platform.shared.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clé maître hors base (AES-GCM + dérivation HMAC pour index aveugles).
 * Production : {@code CRYPTO_KEY} (Base64, 32 octets). Jamais en base ni dans les dumps applicatifs.
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String key) {
}
