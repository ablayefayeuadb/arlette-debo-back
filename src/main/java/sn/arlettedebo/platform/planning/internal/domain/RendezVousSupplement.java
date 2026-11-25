package sn.arlettedebo.platform.planning.internal.domain;

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

/** Supplément accepté avant réalisation (CLI-07). */
@Getter
@Entity
@Table(name = "td_rendez_vous_supplement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RendezVousSupplement extends AuditableEntity {

    @Column(name = "rendez_vous_id", nullable = false, updatable = false)
    private UUID rendezVousId;

    @Column(name = "libelle", nullable = false, updatable = false)
    private String libelle;

    @Column(name = "montant", nullable = false, updatable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Column(name = "accepte_le", nullable = false, updatable = false)
    private Instant accepteLe;

    public RendezVousSupplement(UUID rendezVousId, String libelle, long montant, Instant accepteLe) {
        if (libelle == null || libelle.isBlank()) {
            throw DomainException.invalide("Le libellé du supplément est obligatoire.");
        }
        if (montant < 0) {
            throw DomainException.invalide("Le montant du supplément ne peut pas être négatif.");
        }
        this.rendezVousId = rendezVousId;
        this.libelle = libelle.trim();
        this.montant = montant;
        this.accepteLe = accepteLe;
    }
}
