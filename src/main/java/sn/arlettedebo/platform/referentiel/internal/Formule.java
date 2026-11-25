package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_formule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Formule extends Referentiel {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;
    
    @Column(name = "prix", nullable = false, columnDefinition = "numeric(12,0)")
    private long prix;
    
    @Column(name = "nb_seances")
    private Short nbSeances;
    
    @Column(name = "duree_jours", nullable = false)
    private short dureeJours;
    
    @Column(name = "actif", nullable = false)
    private boolean actif;
}
