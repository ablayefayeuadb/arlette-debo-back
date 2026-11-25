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
import sn.arlettedebo.platform.shared.error.DomainException;

/** Fiche d'attente / retard significative (CLI-06). */
@Getter
@Entity
@Table(name = "td_attente_rdv")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttenteRdv extends AuditableEntity {

    /** Seuil métier (minutes) à partir duquel une fiche d'attente est pertinente. */
    public static final int SEUIL_MINUTES = 15;

    @Column(name = "rendez_vous_id", nullable = false, updatable = false)
    private UUID rendezVousId;

    @Column(name = "attente_minutes", nullable = false, updatable = false)
    private int attenteMinutes;

    @Column(name = "motif", nullable = false, updatable = false)
    private String motif;

    @Column(name = "info_cliente", nullable = false, updatable = false)
    private boolean infoCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false)
    private Decision decision;

    @Column(name = "acteur_id", updatable = false)
    private UUID acteurId;

    @Column(name = "le", nullable = false, updatable = false)
    private Instant le;

    public AttenteRdv(UUID rendezVousId, int attenteMinutes, String motif, boolean infoCliente, Decision decision,
            UUID acteurId, Instant le) {
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("Le motif d'attente est obligatoire.");
        }
        this.rendezVousId = rendezVousId;
        this.attenteMinutes = attenteMinutes;
        this.motif = motif.trim();
        this.infoCliente = infoCliente;
        this.decision = decision;
        this.acteurId = acteurId;
        this.le = le;
    }

    public enum Decision {
        REALLOUER, REPORTER, MAINTENIR
    }
}
