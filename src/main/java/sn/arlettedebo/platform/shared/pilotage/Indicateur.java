package sn.arlettedebo.platform.shared.pilotage;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Indicateur chiffré d'un module.
 *
 * @param code     identifiant stable (ex. {@code caisse.ca}) — clé de comparaison entre périodes
 * @param valeur   valeur numérique ; toujours présente en JSON, {@code null} si non calculable (ex. moyenne sans données)
 * @param unite    FCFA, %, /5, min, nombre
 * @param sens     interprétation de la hausse (évolution favorable ou non)
 */
public record Indicateur(String code, String libelle, @JsonInclude(JsonInclude.Include.ALWAYS) Double valeur, String unite, Niveau niveau, Sens sens, String detail) {

    public static Indicateur nombre(String code, String libelle, long valeur, Sens sens) {
        return new Indicateur(code, libelle, (double) valeur, "nombre", Niveau.INFO, sens, null);
    }

    public static Indicateur montant(String code, String libelle, long valeur, Sens sens) {
        return new Indicateur(code, libelle, (double) valeur, "FCFA", Niveau.INFO, sens, null);
    }

    public static Indicateur taux(String code, String libelle, long numerateur, long denominateur, Sens sens) {
        Double v = denominateur == 0 ? null : Math.round(1000.0 * numerateur / denominateur) / 10.0;
        return new Indicateur(code, libelle, v, "%", Niveau.INFO, sens, numerateur + " / " + denominateur);
    }

    public Indicateur niveau(Niveau n) {
        return new Indicateur(code, libelle, valeur, unite, n, sens, detail);
    }

    public Indicateur detail(String d) {
        return new Indicateur(code, libelle, valeur, unite, niveau, sens, d);
    }

    /** Alerte si la valeur dépasse un seuil (ex. ruptures > 0). */
    public Indicateur alerteSi(boolean condition, Niveau n) {
        return condition ? niveau(n) : niveau(Niveau.OK);
    }

    public enum Niveau { INFO, OK, ATTENTION, ALERTE }

    /** HAUSSE_FAVORABLE : CA, satisfaction… ; BAISSE_FAVORABLE : retards, réclamations… ; NEUTRE. */
    public enum Sens { HAUSSE_FAVORABLE, BAISSE_FAVORABLE, NEUTRE }
}
