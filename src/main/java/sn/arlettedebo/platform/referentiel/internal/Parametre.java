package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Paramètre applicatif modifiable par la Direction (ex. seuil de validation DG). */
@Getter
@Entity
@Table(name = "tp_parametre")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parametre {

    @Id
    @Column(name = "cle", nullable = false, updatable = false)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "description")
    private String description;

    @Column(name = "modifie_par")
    private UUID modifiePar;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    public void modifier(String nouvelleValeur, UUID par, Instant le) {
        this.valeur = nouvelleValeur;
        this.modifiePar = par;
        this.modifieLe = le;
    }
}
