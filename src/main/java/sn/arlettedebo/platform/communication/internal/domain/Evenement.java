package sn.arlettedebo.platform.communication.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Événement du calendrier (Octobre Rose, Black Friday, formations…) : date → responsable → budget → statut. */
@Getter
@Entity
@Table(name = "td_evenement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evenement extends AuditableEntity {

    @Column(name = "titre", nullable = false)
    private String titre;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "perimetre_code")
    private String perimetreCode;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "responsable_id")
    private UUID responsableId;

    @Column(name = "budget", columnDefinition = "numeric(14,0)")
    private Long budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.PREVU;

    @Column(name = "description")
    private String description;

    /** Événement du calendrier (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String titre, Type type, String perimetre, LocalDate debut, LocalDate fin, UUID responsable,
            Long budget, String description) {
    }

    public Evenement(Donnees d) {
        if (d.fin().isBefore(d.debut())) {
            throw DomainException.invalide("La date de d.fin() précède la date de début.");
        }
        this.titre = d.titre();
        this.type = d.type();
        this.perimetreCode = d.perimetre();
        this.dateDebut = d.debut();
        this.dateFin = d.fin();
        this.responsableId = d.responsable();
        this.budget = d.budget();
        this.description = d.description();
    }

    public void changerStatut(Statut statut) {
        this.statut = statut;
    }

    public enum Type { CAMPAGNE, FORMATION, REUNION, EVENEMENT, AUTRE }

    public enum Statut { PREVU, EN_PREPARATION, EN_COURS, TERMINE, ANNULE }
}
