package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_formation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Formation extends Referentiel {

    @Column(name = "obligatoire", nullable = false)
    private boolean obligatoire;
}
