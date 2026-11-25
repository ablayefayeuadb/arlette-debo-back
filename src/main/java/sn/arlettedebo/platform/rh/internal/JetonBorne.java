package sn.arlettedebo.platform.rh.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * QR de la borne de pointage : jeton {@code AD1.SITE.<fenêtre>.<signature>} (HMAC-SHA256, clé serveur).
 * <ul>
 *   <li>infalsifiable sans la clé, qui ne quitte jamais le serveur ;</li>
 *   <li>unique pour tout le site : le même QR sert à tous les utilisateurs tant qu'il est valide ;</li>
 *   <li>valable une fenêtre de rotation (+ la précédente, marge de scan) : une photo du QR expire vite.</li>
 * </ul>
 */
@Component
public class JetonBorne {

    private static final String VERSION = "AD1";
    /** Portée unique : le pointage ne dépend plus d'un salon. */
    public static final String PORTEE = "SITE";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] cle;

    JetonBorne(PointageProperties props) {
        this.cle = props.secret().getBytes(StandardCharsets.UTF_8);
    }

    /** Jeton de la fenêtre contenant {@code maintenant}. */
    Emis emettre(Instant maintenant, int rotationSecondes) {
        long fenetre = maintenant.getEpochSecond() / rotationSecondes;
        String jeton = VERSION + "." + PORTEE + "." + fenetre + "." + signer(fenetre);
        return new Emis(jeton, Instant.ofEpochSecond((fenetre + 1) * rotationSecondes), rotationSecondes);
    }

    /**
     * Vérifie un jeton scanné (signature + fraîcheur).
     *
     * @throws DomainException si le jeton est mal formé, falsifié ou expiré
     */
    void verifier(String jeton, Instant maintenant, int rotationSecondes) {
        String[] parties = jeton == null ? new String[0] : jeton.trim().split("\\.");
        if (parties.length != 4 || !VERSION.equals(parties[0]) || !PORTEE.equals(parties[1])) {
            throw invalide("QR non reconnu : scannez le QR affiché sur la borne.");
        }
        long fenetre;
        try {
            fenetre = Long.parseLong(parties[2]);
        } catch (NumberFormatException e) {
            throw invalide("QR non reconnu.");
        }
        byte[] attendue = signer(fenetre).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(attendue, parties[3].getBytes(StandardCharsets.US_ASCII))) {
            throw invalide("QR invalide (signature).");
        }
        long courante = maintenant.getEpochSecond() / rotationSecondes;
        if (fenetre != courante && fenetre != courante - 1) {
            throw invalide("QR expiré : scannez le QR actuellement affiché sur la borne.");
        }
    }

    private String signer(long fenetre) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            byte[] h = mac.doFinal((VERSION + "|" + PORTEE + "|" + fenetre).getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(java.util.Arrays.copyOf(h, 16));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    private static DomainException invalide(String message) {
        return DomainException.regle("QR_POINTAGE", message);
    }

    public record Emis(String jeton, Instant expireLe, int rotationSecondes) {
    }
}
