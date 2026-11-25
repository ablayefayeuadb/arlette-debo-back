package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Produit ou mode cochable sur une séance de suivi abonnée (catalogue papier). */
@Getter
@Entity
@Table(name = "tp_option_suivi_soin")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionSuiviSoin extends Referentiel {

    @Column(name = "categorie", nullable = false, length = 30)
    private String categorie;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
