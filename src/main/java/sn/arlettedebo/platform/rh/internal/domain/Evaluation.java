package sn.arlettedebo.platform.rh.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

@Getter
@Entity
@Table(name = "td_evaluation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluation extends AuditableEntity {

    @Column(name = "employe_id", nullable = false, updatable = false)
    private UUID employeId;

    @Column(name = "date_evaluation", nullable = false)
    private LocalDate dateEvaluation;

    @Column(name = "note", nullable = false)
    private short note;

    @Column(name = "points_forts")
    private String pointsForts;

    @Column(name = "axes_progres")
    private String axesProgres;

    @Column(name = "evaluateur_id", nullable = false, updatable = false)
    private UUID evaluateurId;

    public Evaluation(UUID employeId, LocalDate date, int note, String pointsForts, String axesProgres, UUID evaluateurId) {
        if (note < 1 || note > 5) {
            throw DomainException.invalide("La note doit être comprise entre 1 et 5.");
        }
        this.employeId = employeId;
        this.dateEvaluation = date;
        this.note = (short) note;
        this.pointsForts = pointsForts;
        this.axesProgres = axesProgres;
        this.evaluateurId = evaluateurId;
    }
}
