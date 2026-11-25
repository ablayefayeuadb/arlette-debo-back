package sn.arlettedebo.platform.planning.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Trace append-only d'une modification de rendez-vous (CLI-03). */
@Getter
@Entity
@Table(name = "td_rendez_vous_historique")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RendezVousHistorique extends AuditableEntity {

    @Column(name = "rendez_vous_id", nullable = false, updatable = false)
    private UUID rendezVousId;

    @Enumerated(EnumType.STRING)
    @Column(name = "champ", nullable = false, updatable = false)
    private Champ champ;

    @Column(name = "avant", updatable = false)
    private String avant;

    @Column(name = "apres", updatable = false)
    private String apres;

    @Column(name = "motif", updatable = false)
    private String motif;

    @Column(name = "acteur_id", updatable = false)
    private UUID acteurId;

    @Column(name = "le", nullable = false, updatable = false)
    private Instant le;

    public RendezVousHistorique(UUID rendezVousId, Champ champ, String avant, String apres, String motif,
            UUID acteurId, Instant le) {
        this.rendezVousId = rendezVousId;
        this.champ = champ;
        this.avant = avant;
        this.apres = apres;
        this.motif = motif == null || motif.isBlank() ? null : motif.trim();
        this.acteurId = acteurId;
        this.le = le;
    }

    public enum Champ {
        STATUT, COIFFEUSE, HORAIRE, PRESTATION, INTENTION, REALISE, NOTE, ANNULATION, ATTENTE, SUPPLEMENT
    }
}
