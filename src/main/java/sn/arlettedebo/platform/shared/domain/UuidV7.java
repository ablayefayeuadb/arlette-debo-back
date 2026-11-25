package sn.arlettedebo.platform.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562) : 48 bits d'horodatage milliseconde + aléa.
 * Les identifiants sont triés dans le temps : insertions séquentielles dans les index B-tree
 * (pas de fragmentation comme avec UUID v4), tout en restant non devinables.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        long millis = System.currentTimeMillis();
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70); // version 7
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // variante RFC 4122
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
