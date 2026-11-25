package sn.arlettedebo.platform.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité dynamique (td_) : traçabilité création/modification et verrouillage optimiste
 * ({@code version}) — deux validations concurrentes d'un même dossier produisent un 409.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @CreatedBy
    @Column(name = "cree_par", updatable = false)
    private UUID creePar;

    @LastModifiedDate
    @Column(name = "modifie_le")
    private Instant modifieLe;

    @LastModifiedBy
    @Column(name = "modifie_par")
    private UUID modifiePar;
}
