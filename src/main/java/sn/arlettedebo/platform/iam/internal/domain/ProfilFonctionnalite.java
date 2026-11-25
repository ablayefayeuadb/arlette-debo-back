package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Fonctionnalité principale d'un profil (fiche de poste) — lecture seule. */
@Getter
@Entity
@Immutable
@Table(name = "tp_profil_fonctionnalite")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfilFonctionnalite {

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "profil_code", nullable = false)
    private String profilCode;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "ordre", nullable = false)
    private short ordre;
}
