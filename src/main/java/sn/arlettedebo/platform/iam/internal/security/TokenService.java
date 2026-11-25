package sn.arlettedebo.platform.iam.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import sn.arlettedebo.platform.iam.CurrentUser;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissement;
import sn.arlettedebo.platform.iam.internal.domain.JetonRafraichissementRepository;

/**
 * Émission des jetons.
 * <ul>
 *   <li>Accès : JWT court (15 min par défaut), claims minimaux — les droits restent côté serveur ;</li>
 *   <li>Rafraîchissement : 256 bits aléatoires, opaques, stockés hachés (SHA-256), à usage unique.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;
    private final JetonRafraichissementRepository jetons;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JetonAcces emettreAcces(CurrentUser u, int versionJeton) {
        Instant maintenant = clock.instant();
        Instant expiration = maintenant.plus(props.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .audience(List.of(props.jwt().audience()))
                .subject(u.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(maintenant)
                .notBefore(maintenant)
                .expiresAt(expiration)
                .claim(PlatformJwtAuthenticationConverter.CLAIM_VERSION, versionJeton)
                .claim("profil", u.profil())
                .claim("nom", u.nom())
                .build();
        String valeur = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(JwtConfig.ALGORITHME).type("JWT").build(), claims))
                .getTokenValue();
        return new JetonAcces(valeur, props.jwt().accessTokenTtl().toSeconds(), expiration);
    }

    /** Crée et persiste un jeton de rafraîchissement ; renvoie la valeur en clair (jamais stockée). */
    public JetonRafraichissementEmis emettreRafraichissement(UUID utilisateurId, UUID famille, String ip, String agent) {
        byte[] octets = new byte[32];
        random.nextBytes(octets);
        String brut = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        Instant maintenant = clock.instant();
        JetonRafraichissement j = new JetonRafraichissement(utilisateurId, empreinte(brut), famille,
                maintenant, maintenant.plus(props.jwt().refreshTokenTtl()), ip, agent);
        jetons.save(j);
        return new JetonRafraichissementEmis(j.getId(), brut, j.getExpireLe());
    }

    public static String empreinte(String brut) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(brut.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record JetonAcces(String valeur, long expireDansSecondes, Instant expireLe) {
    }

    public record JetonRafraichissementEmis(UUID id, String valeur, Instant expireLe) {
    }
}
