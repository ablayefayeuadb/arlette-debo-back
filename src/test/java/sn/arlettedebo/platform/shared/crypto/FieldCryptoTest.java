package sn.arlettedebo.platform.shared.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FieldCryptoTest {

    private FieldCrypto crypto;

    @BeforeEach
    void setUp() {
        crypto = new FieldCrypto(new CryptoProperties("ZGV2LW9ubHktYXJsZXR0ZS1kZWJvLWFlcy0yNTYtYml0cy1taW4hISE="));
        crypto.enregistrer();
    }

    @Test
    void roundTripTexte() {
        String chiffre = crypto.chiffrer("77 123 45 67");
        assertTrue(crypto.estChiffre(chiffre));
        assertEquals("77 123 45 67", crypto.dechiffrer(chiffre));
    }

    @Test
    void ivAleatoire() {
        assertNotEquals(crypto.chiffrer("meme"), crypto.chiffrer("meme"));
    }

    @Test
    void indexTelephoneStable() {
        String a = crypto.rechercheTelephone("77 123 45 67");
        String b = crypto.rechercheTelephone("771234567");
        String c = crypto.rechercheTelephone("+221771234567");
        assertEquals(a, b);
        assertEquals(a, c);
        assertFalse(a.contains("77"));
    }

    @Test
    void indexEmailIgnoreCasse() {
        assertEquals(crypto.rechercheEmail("DG@ArletteDebo.sn"), crypto.rechercheEmail("dg@arlettedebo.sn"));
    }

    @Test
    void roundTripSalaire() {
        String stocke = crypto.chiffrerLong(250_000L);
        assertTrue(stocke.startsWith("v1:"));
        assertEquals(250_000L, crypto.dechiffrerLong(stocke));
    }
}
