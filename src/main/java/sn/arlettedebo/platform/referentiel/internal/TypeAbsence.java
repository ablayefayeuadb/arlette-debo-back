package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_type_absence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TypeAbsence extends Referentiel {

    /** Congé : soumis à décision ; absence/maladie/retard : enregistrés directement. */
    @Column(name = "soumis_validation", nullable = false)
    private boolean soumisValidation;
}
