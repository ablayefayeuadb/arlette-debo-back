package sn.arlettedebo.platform.shared.web;

import java.text.Normalizer;
import java.util.Locale;

/** Recherche plein texte côté serveur : insensible à la casse et aux accents (« ndeye » trouve « Ndèye »). */
public final class Recherche {

    private Recherche() {
    }

    public static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Vrai si la recherche est vide ou contenue dans l'un des champs. */
    public static boolean correspond(String recherche, String... champs) {
        String r = normaliser(recherche);
        if (r.isEmpty()) {
            return true;
        }
        for (String c : champs) {
            if (normaliser(c).contains(r)) {
                return true;
            }
        }
        return false;
    }
}
