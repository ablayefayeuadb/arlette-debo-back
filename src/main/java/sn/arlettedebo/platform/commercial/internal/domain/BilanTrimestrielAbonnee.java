package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Bilan de soins trimestriel d'une abonnée : analyse des objectifs, progrès, difficultés et conclusion. */
@Getter
@Entity
@Table(name = "td_bilan_trimestriel_abonnee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BilanTrimestrielAbonnee extends AuditableEntity {

    @Column(name = "dossier_id", nullable = false, updatable = false)
    private UUID dossierId;

    /** Date du bilan (début de la période de trois mois évaluée). */
    @Column(name = "trimestre_debut", nullable = false)
    private LocalDate trimestreDebut;

    @Column(name = "analyse_objectifs")
    private String analyseObjectifs;

    @Column(name = "progres")
    private String progres;

    @Column(name = "difficultes")
    private String difficultes;

    @Column(name = "habitudes_a_corriger")
    private String habitudesACorriger;

    @Column(name = "recommandations")
    private String recommandations;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", nullable = false)
    private Conclusion conclusion;

    /** Saisie d'un bilan (objet-paramètre). */
    public record Donnees(UUID dossierId, LocalDate trimestreDebut, String analyseObjectifs, String progres, String difficultes,
            String habitudesACorriger, String recommandations, Conclusion conclusion) {
    }

    public BilanTrimestrielAbonnee(Donnees d) {
        this.dossierId = d.dossierId();
        this.trimestreDebut = d.trimestreDebut();
        this.analyseObjectifs = vide(d.analyseObjectifs());
        this.progres = vide(d.progres());
        this.difficultes = vide(d.difficultes());
        this.habitudesACorriger = vide(d.habitudesACorriger());
        this.recommandations = vide(d.recommandations());
        this.conclusion = d.conclusion();
    }

    /** Mise à jour partielle : {@code null} = inchangé, chaîne vide = effacer. */
    public void mettreAJour(Donnees d) {
        if (d.trimestreDebut() != null) {
            this.trimestreDebut = d.trimestreDebut();
        }
        if (d.analyseObjectifs() != null) {
            this.analyseObjectifs = vide(d.analyseObjectifs());
        }
        if (d.progres() != null) {
            this.progres = vide(d.progres());
        }
        if (d.difficultes() != null) {
            this.difficultes = vide(d.difficultes());
        }
        if (d.habitudesACorriger() != null) {
            this.habitudesACorriger = vide(d.habitudesACorriger());
        }
        if (d.recommandations() != null) {
            this.recommandations = vide(d.recommandations());
        }
        if (d.conclusion() != null) {
            this.conclusion = d.conclusion();
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public enum Conclusion { OBJECTIFS_ATTEINTS, PARTIELLEMENT_ATTEINTS, PROGRAMME_A_POURSUIVRE, NOUVEAU_DIAGNOSTIC }
}
