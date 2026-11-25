package sn.arlettedebo.platform.gouvernance.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Accusé de lecture d'une procédure publiée (DOC-02). */
@Getter
@Entity
@Table(name = "td_procedure_lecture")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcedureLecture extends AuditableEntity {

    @Column(name = "procedure_id", nullable = false, updatable = false)
    private UUID procedureId;

    @Column(name = "utilisateur_id", nullable = false, updatable = false)
    private UUID utilisateurId;

    @Column(name = "lu_le", nullable = false, updatable = false)
    private Instant luLe;

    public ProcedureLecture(UUID procedureId, UUID utilisateurId, Instant luLe) {
        this.procedureId = procedureId;
        this.utilisateurId = utilisateurId;
        this.luLe = luLe;
    }
}
