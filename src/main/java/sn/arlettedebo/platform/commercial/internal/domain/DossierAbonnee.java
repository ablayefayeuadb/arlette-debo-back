package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Dossier de l'abonnée (fiche riche : informations personnelles, abonnement, profil capillaire et routine). */
@Getter
@Entity
@Table(name = "td_dossier_abonnee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DossierAbonnee extends AuditableEntity {

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "perimetre_code", nullable = false, updatable = false)
    private String perimetreCode;

    /** Compte portail abonnée (1:1) — null = accès pas encore ouvert. */
    @Column(name = "utilisateur_id", unique = true)
    private UUID utilisateurId;

    @Column(name = "prenom")
    private String prenom;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "profession")
    private String profession;

    @Column(name = "date_inscription")
    private LocalDate dateInscription;

    @Column(name = "prise_en_charge")
    private String priseEnCharge;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_abonnement")
    private TypeAbonnement typeAbonnement;

    /** Profil capillaire et routine (clés libres : typeCheveux, porosite, longueur, objectifs…). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profil_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> profilJson = new LinkedHashMap<>();

    @Column(name = "recommandations")
    private String recommandations;

    /** Fiche à la création (objet-paramètre : champs nommés). */
    public record Donnees(UUID clientId, String perimetre, String prenom, String adresse, String profession,
            LocalDate dateInscription, String priseEnCharge, TypeAbonnement typeAbonnement, Map<String, Object> profilJson,
            String recommandations) {
    }

    public DossierAbonnee(Donnees d) {
        this.clientId = d.clientId();
        this.perimetreCode = d.perimetre();
        this.prenom = d.prenom();
        this.adresse = d.adresse();
        this.profession = d.profession();
        this.dateInscription = d.dateInscription();
        this.priseEnCharge = d.priseEnCharge();
        this.typeAbonnement = d.typeAbonnement();
        this.profilJson = d.profilJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(d.profilJson());
        this.recommandations = d.recommandations();
    }

    /**
     * Mise à jour partielle : un champ {@code null} reste inchangé, une chaîne vide efface la valeur.
     * Le profil, s'il est fourni, remplace entièrement le précédent.
     */
    public void mettreAJour(Donnees d) {
        if (d.prenom() != null) {
            this.prenom = vide(d.prenom());
        }
        if (d.adresse() != null) {
            this.adresse = vide(d.adresse());
        }
        if (d.profession() != null) {
            this.profession = vide(d.profession());
        }
        if (d.dateInscription() != null) {
            this.dateInscription = d.dateInscription();
        }
        if (d.priseEnCharge() != null) {
            this.priseEnCharge = vide(d.priseEnCharge());
        }
        if (d.typeAbonnement() != null) {
            this.typeAbonnement = d.typeAbonnement();
        }
        if (d.profilJson() != null) {
            this.profilJson = new LinkedHashMap<>(d.profilJson());
        }
        if (d.recommandations() != null) {
            this.recommandations = vide(d.recommandations());
        }
    }

    /** Lie le dossier à un compte ABONNEE (une seule fois). */
    public void lierCompte(UUID utilisateurId) {
        if (utilisateurId == null) {
            throw DomainException.invalide("Compte requis.");
        }
        if (this.utilisateurId != null && !this.utilisateurId.equals(utilisateurId)) {
            throw DomainException.regle("ACCES_EXISTANT", "Ce dossier a déjà un compte portail.");
        }
        this.utilisateurId = utilisateurId;
    }

    public void detacherCompte() {
        this.utilisateurId = null;
    }

    private static String vide(String s) {
        return s.isBlank() ? null : s.trim();
    }

    public enum TypeAbonnement { MENSUEL, BIMENSUEL, LOCKS }
}
