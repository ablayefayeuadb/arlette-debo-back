package sn.arlettedebo.platform.iam.internal.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT signés HS256 (Nimbus). Le décodeur vérifie signature, émetteur, audience et dates
 * (tolérance d'horloge de 30 s). Une clé de moins de 256 bits empêche le démarrage.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    static final MacAlgorithm ALGORITHME = MacAlgorithm.HS256;

    @Bean
    SecretKey jwtSecretKey(SecurityProperties props) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(props.jwt().secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.jwt.secret doit être encodé en Base64.", e);
        }
        if (octets.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret doit faire au moins 256 bits (32 octets).");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, SecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHME).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(props.jwt().audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience invalide", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(props.jwt().issuer()),
                audience));
        return decoder;
    }
}
