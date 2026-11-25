package sn.arlettedebo.platform.shared.telephone;

import sn.arlettedebo.platform.shared.error.DomainException;

/** Numéros sénégalais : saisie → masque · stockage masqué · envoi E.164. */
public final class Telephones {

    private Telephones() {
    }

    /** 9 chiffres nationaux (préfixe 3 ou 7), indicatif 221 accepté. */
    public static String chiffres(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String d = brut.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("221")) {
            d = d.substring(3);
        }
        if (!d.matches("[37]\\d{8}")) {
            throw DomainException.invalide("Numéro de téléphone invalide (9 chiffres, ex. 77 123 45 67).");
        }
        return d;
    }

    /** Persisté / affiché : « 77 123 45 67 ». */
    public static String masquer(String brut) {
        String d = chiffres(brut);
        if (d == null) {
            return null;
        }
        return d.substring(0, 2) + " " + d.substring(2, 5) + " " + d.substring(5, 7) + " " + d.substring(7, 9);
    }

    /** Envoi SMS : « +221771234567 ». */
    public static String e164(String brut) {
        String d = chiffres(brut);
        return d == null ? null : "+221" + d;
    }
}
