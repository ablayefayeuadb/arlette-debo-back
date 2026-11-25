package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_type_decision")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TypeDecision extends Referentiel {

}
