package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_mode_paiement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModePaiement extends Referentiel {

    @Column(name = "ordre", nullable = false)
    private short ordre;
    
    @Column(name = "actif", nullable = false)
    private boolean actif;
}
