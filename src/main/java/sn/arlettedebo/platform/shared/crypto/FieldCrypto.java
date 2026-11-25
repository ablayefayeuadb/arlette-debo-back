package sn.arlettedebo.platform.shared.crypto;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.shared.error.DomainException;
import sn.arlettedebo.platform.shared.telephone.Telephones;

/**
 * Chiffrement réversible AES-256-GCM (préfixe {@code v1:}) et index aveugle HMAC-SHA256
 * pour égalité / unicité sans exposer la clair dans les index SQL.
 */
@Component
public class FieldCrypto {

    static final String PREFIXE = "v1:";
    private static final int IV_OCTETS = 12;
    private static final int TAG_OCTETS = 128;

    private static volatile FieldCrypto instance;

    private final SecretKey aes;
    private final SecretKey hmac;
    private final SecureRandom aleatoire = new SecureRandom();

    public FieldCrypto(CryptoProperties props) {
        byte[] master = decoderCle(props.key());
        this.aes = new SecretKeySpec(deriver(master, "aes-gcm-v1"), "AES");
        this.hmac = new SecretKeySpec(deriver(master, "hmac-blind-v1"), "HmacSHA256");
        Arrays.fill(master, (byte) 0);
    }

    @PostConstruct
    void enregistrer() {
        instance = this;
    }

    /** Accès depuis les entités JPA ({@code @PrePersist}) — initialisé au démarrage Spring. */
    public static FieldCrypto requis() {
        FieldCrypto c = instance;
        if (c == null) {
            throw new IllegalStateException("FieldCrypto non initialisé (démarrage Spring incomplet).");
        }
        return c;
    }

    public String chiffrer(String clair) {
        if (clair == null) {
            return null;
        }
        if (estChiffre(clair)) {
            return clair;
        }
        try {
            byte[] iv = new byte[IV_OCTETS];
            aleatoire.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(TAG_OCTETS, iv));
            byte[] chiffre = cipher.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + chiffre.length);
            buf.put(iv);
            buf.put(chiffre);
            return PREFIXE + Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec du chiffrement AES-GCM.", e);
        }
    }

    public String dechiffrer(String stocke) {
        if (stocke == null) {
            return null;
        }
        if (!estChiffre(stocke)) {
            return stocke;
        }
        try {
            byte[] brut = Base64.getDecoder().decode(stocke.substring(PREFIXE.length()));
            if (brut.length < IV_OCTETS + 16) {
                throw DomainException.invalide("Donnée chiffrée invalide.");
            }
            byte[] iv = Arrays.copyOfRange(brut, 0, IV_OCTETS);
            byte[] corps = Arrays.copyOfRange(brut, IV_OCTETS, brut.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(TAG_OCTETS, iv));
            return new String(cipher.doFinal(corps), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Échec du déchiffrement AES-GCM (clé incorrecte ou donnée altérée).", e);
        }
    }

    public boolean estChiffre(String valeur) {
        return valeur != null && valeur.startsWith(PREFIXE);
    }

    /** Index aveugle téléphone : chiffres nationaux (9), indépendant du masque d'affichage. */
    public String rechercheTelephone(String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return null;
        }
        String chiffres;
        try {
            chiffres = Telephones.chiffres(telephone);
        } catch (RuntimeException ex) {
            chiffres = telephone.replaceAll("\\D", "");
            if (chiffres.startsWith("221") && chiffres.length() == 12) {
                chiffres = chiffres.substring(3);
            }
            if (chiffres.isEmpty()) {
                return null;
            }
        }
        return hmac("tel:" + chiffres);
    }

    /** Index aveugle e-mail : minuscules, trim. */
    public String rechercheEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return hmac("email:" + email.trim().toLowerCase(Locale.ROOT));
    }

    public String chiffrerLong(long valeur) {
        return chiffrer(Long.toString(valeur));
    }

    public long dechiffrerLong(String stocke) {
        if (stocke == null || stocke.isBlank()) {
            return 0L;
        }
        String clair = dechiffrer(stocke);
        try {
            return Long.parseLong(clair.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Salaire chiffré illisible.", e);
        }
    }

    private String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmac);
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Échec HMAC index aveugle.", e);
        }
    }

    private static byte[] decoderCle(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Clé de chiffrement absente : définir app.crypto.key / CRYPTO_KEY (Base64, 32 octets).");
        }
        byte[] brut;
        try {
            brut = Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CRYPTO_KEY doit être en Base64.", e);
        }
        if (brut.length < 32) {
            throw new IllegalStateException("CRYPTO_KEY trop courte (32 octets requis, openssl rand -base64 32).");
        }
        return Arrays.copyOf(brut, 32);
    }

    private static byte[] deriver(byte[] master, String etiquette) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(master);
            md.update(etiquette.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
