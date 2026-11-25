package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_type_variable_paie")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TypeVariablePaie extends Referentiel {

    /** +1 gain, -1 retenue. */
    @Column(name = "sens", nullable = false)
    private short sens;
}
