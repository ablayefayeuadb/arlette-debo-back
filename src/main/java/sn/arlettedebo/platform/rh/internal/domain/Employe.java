package sn.arlettedebo.platform.rh.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.crypto.EncryptedLongConverter;
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Dossier du personnel. {@code perimetreCode} nul = transverse (Direction, plusieurs périmètres). */
@Getter
@Entity
@Table(name = "td_employe")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employe extends AuditableEntity {

    @Column(name = "utilisateur_id")
    private UUID utilisateurId;

    @Column(name = "matricule", nullable = false, updatable = false)
    private String matricule;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "poste", nullable = false)
    private String poste;

    @Column(name = "grade")
    private String grade;

    @Column(name = "perimetre_code")
    private String perimetreCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telephone", columnDefinition = "text")
    private String telephone;

    @Column(name = "date_entree", nullable = false)
    private LocalDate dateEntree;

    @Column(name = "date_sortie")
    private LocalDate dateSortie;

    @Enumerated(EnumType.STRING)
    @Column(name = "contrat", nullable = false)
    private Contrat contrat;

    /** Donnée sensible : visible uniquement en gestion RH ou par la DG (AES-GCM en base). */
    @Convert(converter = EncryptedLongConverter.class)
    @Column(name = "salaire_base", nullable = false, columnDefinition = "text")
    private long salaireBase;

    @Column(name = "heure_prise_poste", nullable = false)
    private LocalTime heurePrisePoste;

    @Column(name = "heure_fin_poste", nullable = false)
    private LocalTime heureFinPoste;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Matrice des compétences, reliée au planning ({@code tr_employe_competence}) — chargée à la demande (graphe « dossier »). */
    @BatchSize(size = 32)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tr_employe_competence", joinColumns = @JoinColumn(name = "employe_id"))
    private Set<Competence> competences = new HashSet<>();

    /** Formations suivies ({@code tr_employe_formation}). */
    @BatchSize(size = 32)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tr_employe_formation", joinColumns = @JoinColumn(name = "employe_id"))
    private Set<FormationSuivie> formations = new HashSet<>();

    /**
     * Données d'un dossier du personnel (création et modification).
     *
     * @param salaireBase     absent = inchangé (modification) ou 0 (création)
     * @param heurePrisePoste absente = inchangée (modification) ou 9 h 30 (création)
     * @param heureFinPoste   absente = inchangée (modification) ou heure de fin par défaut fixée par la DG (création)
     */
    public record Dossier(String nom, String poste, String grade, String perimetreCode, String telephone, LocalDate dateEntree,
            Contrat contrat, Long salaireBase, LocalTime heurePrisePoste, LocalTime heureFinPoste, UUID utilisateurId) {
    }

    public Employe(String matricule, Dossier d) {
        this.matricule = matricule;
        this.dateEntree = d.dateEntree();
        this.utilisateurId = d.utilisateurId();
        this.salaireBase = d.salaireBase() == null ? 0 : d.salaireBase();
        this.heurePrisePoste = d.heurePrisePoste() == null ? LocalTime.of(9, 30) : d.heurePrisePoste();
        this.heureFinPoste = d.heureFinPoste() == null ? LocalTime.of(19, 0) : d.heureFinPoste();
        appliquer(d);
    }

    /** Met à jour l'identité et le poste ; la date d'entrée et le compte lié ne changent pas. */
    public void modifier(Dossier d) {
        appliquer(d);
    }

    private void appliquer(Dossier d) {
        this.nom = d.nom();
        this.poste = d.poste();
        this.grade = d.grade();
        this.perimetreCode = d.perimetreCode();
        this.telephone = d.telephone();
        this.contrat = d.contrat();
        if (d.salaireBase() != null) {
            this.salaireBase = d.salaireBase();
        }
        if (d.heurePrisePoste() != null) {
            this.heurePrisePoste = d.heurePrisePoste();
        }
        if (d.heureFinPoste() != null) {
            this.heureFinPoste = d.heureFinPoste();
        }
        if (!heureFinPoste.isAfter(heurePrisePoste)) {
            throw sn.arlettedebo.platform.shared.error.DomainException.invalide("L'heure de fin de poste doit être après l'heure de prise de poste.");
        }
    }

    public void sortir(LocalDate le) {
        if (!actif) {
            throw DomainException.etatInvalide("Ce dossier est déjà sorti des effectifs.");
        }
        if (le.isBefore(dateEntree)) {
            throw DomainException.invalide("La date de sortie précède la date d'entrée.");
        }
        this.actif = false;
        this.dateSortie = le;
    }

    public void reintegrer() {
        this.actif = true;
        this.dateSortie = null;
    }

    public void lierCompte(UUID utilisateurId) {
        this.utilisateurId = utilisateurId;
    }

    public void definirCompetence(String prestationCode, short niveau, UUID validePar, LocalDate le) {
        competences.removeIf(c -> c.getPrestationCode().equals(prestationCode));
        competences.add(new Competence(prestationCode, niveau, le, validePar));
    }

    public void definirFormation(String formationCode, FormationSuivie.Statut statut, LocalDate valideeLe) {
        formations.removeIf(f -> f.getFormationCode().equals(formationCode));
        formations.add(new FormationSuivie(formationCode, statut, statut == FormationSuivie.Statut.VALIDEE ? valideeLe : null));
    }

    public enum Contrat {
        CDI, CDD, STAGE, PRESTATAIRE
    }

    @Getter
    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class FormationSuivie {

        @Column(name = "formation_code", nullable = false)
        private String formationCode;

        @Enumerated(EnumType.STRING)
        @Column(name = "statut", nullable = false)
        private Statut statut;

        @Column(name = "validee_le")
        private LocalDate valideeLe;

        FormationSuivie(String formationCode, Statut statut, LocalDate valideeLe) {
            this.formationCode = formationCode;
            this.statut = statut;
            this.valideeLe = valideeLe;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FormationSuivie f && f.formationCode.equals(formationCode);
        }

        @Override
        public int hashCode() {
            return formationCode.hashCode();
        }

        public enum Statut {
            PLANIFIEE, SUIVIE, VALIDEE
        }
    }

    @Getter
    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Competence {

        @Column(name = "prestation_code", nullable = false)
        private String prestationCode;

        @Column(name = "niveau", nullable = false)
        private short niveau;

        @Column(name = "validee_le")
        private LocalDate valideeLe;

        @Column(name = "validee_par")
        private UUID valideePar;

        Competence(String prestationCode, short niveau, LocalDate valideeLe, UUID valideePar) {
            this.prestationCode = prestationCode;
            this.niveau = niveau;
            this.valideeLe = valideeLe;
            this.valideePar = valideePar;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Competence c && c.prestationCode.equals(prestationCode);
        }

        @Override
        public int hashCode() {
            return prestationCode.hashCode();
        }
    }
}
