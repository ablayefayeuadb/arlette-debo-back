package sn.arlettedebo.platform.shared.sms;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Textes SMS métier (FR, courts). */
public final class SmsMessages {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private SmsMessages() {
    }

    public static String compteCree(String email, String motDePasseProvisoire, String loginUrl) {
        return "Arlette Debo — votre accès pro est prêt.\n"
                + "Connectez-vous : " + loginUrl + "\n"
                + "Identifiant : " + email + "\n"
                + "Mot de passe provisoire : " + motDePasseProvisoire + "\n"
                + "Changez-le à la 1re connexion.";
    }

    public static String accesAbonnee(String email, String motDePasseProvisoire, String loginUrl) {
        return "Arlette Debo — votre espace abonnée est prêt.\n"
                + "Connectez-vous : " + loginUrl + "\n"
                + "Identifiant : " + email + "\n"
                + "Mot de passe provisoire : " + motDePasseProvisoire + "\n"
                + "Changez-le à la 1re connexion.";
    }

    public static String rdvConfirme(String salon, LocalDate jour, LocalTime heure, String prestation, String reference) {
        return "Arlette Debo — RDV confirmé.\n"
                + salon + " · " + capitaliser(jour.format(JOUR)) + " à " + heure.format(HEURE) + "\n"
                + "Prestation : " + prestation + "\n"
                + "Réf. " + reference + "\n"
                + "Présentez-vous 5 min avant.";
    }

    public static String rdvDemandeEnLigne(String salon, LocalDate jour, LocalTime heure, String prestation, String reference) {
        return "Arlette Debo — demande de RDV reçue.\n"
                + salon + " · " + capitaliser(jour.format(JOUR)) + " à " + heure.format(HEURE) + " · " + prestation + "\n"
                + "Réf. " + reference + "\n"
                + "Le salon vous confirmera sous peu.";
    }

    private static String capitaliser(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
