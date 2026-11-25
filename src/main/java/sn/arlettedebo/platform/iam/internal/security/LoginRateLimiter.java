package sn.arlettedebo.platform.iam.internal.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Limitation des tentatives de connexion par adresse IP (fenêtre glissante d'une minute),
 * en complément du verrouillage par compte : freine la pulvérisation de mots de passe
 * sur plusieurs comptes depuis une même source.
 */
@Component
public class LoginRateLimiter {

    private final Cache<String, AtomicInteger> compteurs = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100_000)
            .build();
    private final int maxParMinute;

    LoginRateLimiter(SecurityProperties props) {
        this.maxParMinute = props.login().maxPerIpPerMinute();
    }

    public void verifier(String ip) {
        int n = compteurs.get(ip == null ? "?" : ip, k -> new AtomicInteger()).incrementAndGet();
        if (n > maxParMinute) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "TROP_DE_TENTATIVES",
                    "Trop de tentatives de connexion. Réessayez dans une minute.");
        }
    }
}
