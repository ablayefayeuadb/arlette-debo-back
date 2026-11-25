package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** Donnée de référence (tp_) identifiée par un code métier stable. */
@Getter
@MappedSuperclass
public abstract class Referentiel {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    protected Referentiel() {
    }

    protected Referentiel(String code, String libelle) {
        this.code = code;
        this.libelle = libelle;
    }

    protected void renommer(String libelle) {
        this.libelle = libelle;
    }
}
