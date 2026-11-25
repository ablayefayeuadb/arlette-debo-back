package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Prestation proposée dans un salon — créée et tenue à jour par le Manager. */
@Getter
@Entity
@Table(name = "td_prestation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prestation extends Referentiel {

    @Column(name = "perimetre_code", nullable = false, updatable = false)
    private String perimetreCode;

    @Column(name = "duree_min", nullable = false)
    private short dureeMin;

    @Column(name = "prix", nullable = false, columnDefinition = "numeric(12,0)")
    private long prix;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    /** Catégorie métier (ABO_MENSUEL, SEANCE_SKY…) ; facultative. */
    @Column(name = "categorie_code", length = 40)
    private String categorieCode;

    /** Catégories de prestation autorisées. */
    public static final java.util.Set<String> CATEGORIES = java.util.Set.of("ABO_BIMENSUEL", "ABO_MENSUEL", "ABO_LOCKS",
            "SEANCE_SKY", "SEANCE_LOCKS", "SEANCE_CHALLENGE", "AUTRE");

    Prestation(String code, String libelle, String perimetreCode, short dureeMin, long prix, short ordre,
            String categorieCode) {
        super(code, libelle);
        this.perimetreCode = perimetreCode;
        this.dureeMin = dureeMin;
        this.prix = prix;
        this.ordre = ordre;
        this.actif = true;
        this.categorieCode = categorieCode;
    }

    void modifier(String libelle, short dureeMin, long prix, boolean actif, short ordre, String categorieCode) {
        renommer(libelle);
        exigerDuree(dureeMin);
        exigerPrix(prix);
        this.dureeMin = dureeMin;
        this.prix = prix;
        this.actif = actif;
        this.ordre = ordre;
        this.categorieCode = categorieCode;
    }

    /** Catégorie vide ⇒ null ; sinon doit appartenir à la liste autorisée. */
    static String categorie(String categorieCode) {
        if (categorieCode == null || categorieCode.isBlank()) {
            return null;
        }
        String c = categorieCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!CATEGORIES.contains(c)) {
            throw DomainException.invalide("Catégorie de prestation inconnue : " + categorieCode + ".");
        }
        return c;
    }

    static void exigerDuree(int dureeMin) {
        if (dureeMin < 5 || dureeMin > 480) {
            throw DomainException.invalide("Durée entre 5 et 480 minutes.");
        }
    }

    static void exigerPrix(long prix) {
        if (prix < 0 || prix > 10_000_000) {
            throw DomainException.invalide("Prix invalide.");
        }
    }
}
