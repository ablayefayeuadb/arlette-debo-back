package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_regle_gestion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegleGestion extends Referentiel {

    @Column(name = "module_code", nullable = false)
    private String moduleCode;
    
    @Column(name = "parametrage", nullable = false)
    private String parametrage;
    
    @Column(name = "ordre", nullable = false)
    private short ordre;
}
