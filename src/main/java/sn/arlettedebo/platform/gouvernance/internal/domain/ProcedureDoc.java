package sn.arlettedebo.platform.gouvernance.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Procédure métier versionnée (DOC-01). */
@Getter
@Entity
@Table(name = "td_procedure")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcedureDoc extends AuditableEntity {

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "activite", nullable = false)
    private String activite;

    @Column(name = "proprietaire", nullable = false)
    private String proprietaire;

    @Column(name = "version_num", nullable = false)
    private int versionNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "resume")
    private String resume;

    @Column(name = "fichier_chemin")
    private String fichierChemin;

    @Column(name = "fichier_nom")
    private String fichierNom;

    @Column(name = "fichier_type")
    private String fichierType;

    @Column(name = "fichier_taille")
    private Long fichierTaille;

    @Column(name = "publie_le")
    private Instant publieLe;

    public ProcedureDoc(String titre, String activite, String proprietaire, String resume) {
        this.titre = exiger(titre, "Le titre est obligatoire.");
        this.activite = exiger(activite, "L'activité est obligatoire.");
        this.proprietaire = exiger(proprietaire, "Le propriétaire est obligatoire.");
        this.resume = resume == null || resume.isBlank() ? null : resume.trim();
        this.versionNum = 1;
        this.statut = Statut.BROUILLON;
    }

    public void mettreAJour(String titre, String activite, String proprietaire, String resume) {
        if (statut == Statut.OBSOLETE) {
            throw DomainException.etatInvalide("Procédure obsolète : créez une nouvelle version.");
        }
        this.titre = exiger(titre, "Le titre est obligatoire.");
        this.activite = exiger(activite, "L'activité est obligatoire.");
        this.proprietaire = exiger(proprietaire, "Le propriétaire est obligatoire.");
        this.resume = resume == null || resume.isBlank() ? null : resume.trim();
    }

    public void attacherFichier(String chemin, String nom, String type, long taille) {
        this.fichierChemin = chemin;
        this.fichierNom = nom;
        this.fichierType = type;
        this.fichierTaille = taille;
    }

    public void publier(Instant maintenant) {
        if (statut == Statut.OBSOLETE) {
            throw DomainException.etatInvalide("Impossible de republier une procédure obsolète.");
        }
        this.statut = Statut.PUBLIE;
        this.publieLe = maintenant;
    }

    public void obsoletiser() {
        if (statut != Statut.PUBLIE) {
            throw DomainException.etatInvalide("Seule une procédure publiée peut être rendue obsolète.");
        }
        this.statut = Statut.OBSOLETE;
    }

    private static String exiger(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw DomainException.invalide(msg);
        }
        return v.trim();
    }

    public enum Statut {
        BROUILLON, PUBLIE, OBSOLETE
    }
}
