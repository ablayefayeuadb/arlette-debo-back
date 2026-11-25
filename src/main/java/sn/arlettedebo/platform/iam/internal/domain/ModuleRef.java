package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Module fonctionnel (colonne de la matrice). */
@Getter
@Entity
@Immutable
@Table(name = "tp_module")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModuleRef {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "description")
    private String description;

    @Column(name = "icone")
    private String icone;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif;
}
