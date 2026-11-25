package sn.arlettedebo.platform.rh.internal.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Élément variable de paie (prime, heures sup., commission, retenue, avance) — confidentiel. */
@Getter
@Entity
@Table(name = "td_variable_paie")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariablePaie extends AuditableEntity {

    @Column(name = "employe_id", nullable = false, updatable = false)
    private UUID employeId;

    /** Format AAAA-MM. */
    @Column(name = "mois", nullable = false, length = 7)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String mois;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "montant", nullable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Column(name = "commentaire")
    private String commentaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.SAISIE;

    @Column(name = "saisi_par", nullable = false, updatable = false)
    private UUID saisiPar;

    @Column(name = "transmise_le")
    private Instant transmiseLe;

    public VariablePaie(UUID employeId, String mois, String typeCode, long montant, String commentaire, UUID saisiPar) {
        if (montant <= 0) {
            throw DomainException.invalide("Le montant doit être positif (le sens est porté par le type).");
        }
        this.employeId = employeId;
        this.mois = mois;
        this.typeCode = typeCode;
        this.montant = montant;
        this.commentaire = commentaire;
        this.saisiPar = saisiPar;
    }

    public void transmettre(Instant le) {
        if (statut == Statut.SAISIE) {
            statut = Statut.TRANSMISE;
            transmiseLe = le;
        }
    }

    public enum Statut {
        SAISIE, TRANSMISE
    }
}
