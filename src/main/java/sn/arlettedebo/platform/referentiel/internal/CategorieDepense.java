package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_categorie_depense")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategorieDepense extends Referentiel {

    @Column(name = "ordre", nullable = false)
    private short ordre;
}
