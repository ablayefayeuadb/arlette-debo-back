package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/**
 * Trace d'audit d'un traitement de signalement : auteur, date, statut avant/après, commentaire.
 * Append-only (pas de modification métier après création).
 */
@Getter
@Entity
@Table(name = "td_traitement_signalement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S2160") // égalité JPA via BaseEntity / AuditableEntity (proxy Hibernate)
public class TraitementSignalement extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signalement_id", nullable = false, updatable = false)
    private Signalement signalement;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_avant", nullable = false, updatable = false)
    private Signalement.Statut statutAvant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_apres", nullable = false, updatable = false)
    private Signalement.Statut statutApres;

    @Column(name = "traite_par", nullable = false, updatable = false)
    private UUID traitePar;

    @Column(name = "traite_le", nullable = false, updatable = false)
    private Instant traiteLe;

    @Column(name = "commentaire", updatable = false)
    private String commentaire;

    TraitementSignalement(Signalement signalement, Signalement.Statut avant, Signalement.Statut apres,
            UUID par, Instant le, String commentaire) {
        this.signalement = signalement;
        this.statutAvant = avant;
        this.statutApres = apres;
        this.traitePar = par;
        this.traiteLe = le;
        this.commentaire = commentaire == null || commentaire.isBlank() ? null : commentaire.trim();
    }
}
