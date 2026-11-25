package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Profil = une fonction de la fiche de poste (ligne de la matrice). */
@Getter
@Entity
@Table(name = "tp_profil")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profil {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "libelle_court", nullable = false)
    private String libelleCourt;

    @Column(name = "finalite", nullable = false)
    private String finalite;

    @Column(name = "icone")
    private String icone;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    /** Périmètres par défaut ({@code tr_profil_perimetre}). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_profil_perimetre", joinColumns = @JoinColumn(name = "profil_code"))
    @Column(name = "perimetre_code", nullable = false)
    private Set<String> perimetres = new HashSet<>();

    /** Rattache un nouveau salon aux périmètres par défaut du profil. */
    public void rattacher(String perimetre) {
        perimetres.add(perimetre);
    }
}
