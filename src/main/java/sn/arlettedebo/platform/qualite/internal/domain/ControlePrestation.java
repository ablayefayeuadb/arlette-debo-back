package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Contrôle d'une prestation (accueil, technique, hygiène, délai). Non conforme ⇒ non-conformité. */
@Getter
@Entity
@Table(name = "td_controle_prestation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlePrestation extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "date_controle", nullable = false)
    private Instant dateControle;

    @Column(name = "rendez_vous_id")
    private UUID rendezVousId;

    @Column(name = "prestation_code")
    private String prestationCode;

    @Column(name = "coiffeuse_id")
    private UUID coiffeuseId;

    @Column(name = "controleur_id", nullable = false)
    private UUID controleurId;

    @Column(name = "note_accueil", nullable = false)
    private short noteAccueil;

    @Column(name = "note_technique", nullable = false)
    private short noteTechnique;

    @Column(name = "note_hygiene", nullable = false)
    private short noteHygiene;

    @Column(name = "note_delai", nullable = false)
    private short noteDelai;

    @Column(name = "conforme", nullable = false)
    private boolean conforme;

    @Column(name = "commentaire")
    private String commentaire;

    /** Réalisé par la Coordinatrice au titre de la délégation de la Manager. */
    @Column(name = "par_delegation", nullable = false)
    private boolean parDelegation;

    /** Grille de contrôle (notes 1 à 5) (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, Instant date, UUID rendezVousId, String prestation, UUID coiffeuseId,
            UUID controleurId, int accueil, int technique, int hygiene, int delai, String commentaire,
            boolean parDelegation) {
    }

    public ControlePrestation(Donnees d) {
        for (int n : new int[] {d.accueil(), d.technique(), d.hygiene(), d.delai()}) {
            if (n < 1 || n > 5) {
                throw DomainException.invalide("Les notes vont de 1 à 5.");
            }
        }
        this.perimetreCode = d.perimetre();
        this.dateControle = d.date();
        this.rendezVousId = d.rendezVousId();
        this.prestationCode = d.prestation();
        this.coiffeuseId = d.coiffeuseId();
        this.controleurId = d.controleurId();
        this.noteAccueil = (short) d.accueil();
        this.noteTechnique = (short) d.technique();
        this.noteHygiene = (short) d.hygiene();
        this.noteDelai = (short) d.delai();
        this.conforme = Math.min(Math.min(d.accueil(), d.technique()), Math.min(d.hygiene(), d.delai())) >= 3;
        this.commentaire = d.commentaire();
        this.parDelegation = d.parDelegation();
    }
}
