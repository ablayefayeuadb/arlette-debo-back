package sn.arlettedebo.platform.iam.internal.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Politique de mot de passe et génération de mots de passe provisoires. */
@Component
public class PasswordPolicy {

    private static final int LONGUEUR_MIN = 10;
    private static final String MAJ = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MIN = "abcdefghijkmnpqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SPECIAUX = "@#%+=!?";
    private final SecureRandom random = new SecureRandom();

    public void verifier(String motDePasse, String email) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN || motDePasse.length() > 72) {
            throw DomainException.invalide("Le mot de passe doit contenir entre 10 et 72 caractères.");
        }
        boolean maj = motDePasse.chars().anyMatch(Character::isUpperCase);
        boolean min = motDePasse.chars().anyMatch(Character::isLowerCase);
        boolean chiffre = motDePasse.chars().anyMatch(Character::isDigit);
        if (!maj || !min || !chiffre) {
            throw DomainException.invalide("Le mot de passe doit contenir une majuscule, une minuscule et un chiffre.");
        }
        if (email != null && motDePasse.toLowerCase().contains(email.split("@")[0].toLowerCase())) {
            throw DomainException.invalide("Le mot de passe ne doit pas contenir l'identifiant.");
        }
    }

    /** Mot de passe provisoire de 14 caractères respectant la politique (caractères ambigus exclus). */
    public String genererProvisoire() {
        String tous = MAJ + MIN + CHIFFRES + SPECIAUX;
        StringBuilder sb = new StringBuilder();
        sb.append(MAJ.charAt(random.nextInt(MAJ.length())));
        sb.append(MIN.charAt(random.nextInt(MIN.length())));
        sb.append(CHIFFRES.charAt(random.nextInt(CHIFFRES.length())));
        sb.append(SPECIAUX.charAt(random.nextInt(SPECIAUX.length())));
        while (sb.length() < 14) {
            sb.append(tous.charAt(random.nextInt(tous.length())));
        }
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }
}
