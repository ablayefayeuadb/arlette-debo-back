package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Signalement d'une coiffeuse : incident, insatisfaction, besoin matériel, anomalie. */
@Getter
@Entity
@Table(name = "td_signalement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signalement extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "date_signalement", nullable = false)
    private Instant dateSignalement;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private UUID auteurId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.NOUVEAU;

    @Column(name = "traite_par")
    private UUID traitePar;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @BatchSize(size = 32)
    @OrderBy("traiteLe asc")
    @OneToMany(mappedBy = "signalement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TraitementSignalement> traitements = new ArrayList<>();

    public Signalement(String perimetre, Instant date, UUID auteur, String type, String description) {
        this.perimetreCode = perimetre;
        this.dateSignalement = date;
        this.auteurId = auteur;
        this.typeCode = type;
        this.description = description;
    }

    /** Fait avancer le statut et enregistre une trace d'audit immuable. */
    public TraitementSignalement avancer(Statut cible, UUID par, Instant le, String commentaire) {
        if (cible.ordinal() <= statut.ordinal()) {
            throw DomainException.etatInvalide("Statut déjà atteint.");
        }
        Statut avant = statut;
        statut = cible;
        traitePar = par;
        traiteLe = le;
        TraitementSignalement t = new TraitementSignalement(this, avant, cible, par, le, commentaire);
        traitements.add(t);
        return t;
    }

    public enum Statut { NOUVEAU, PRIS_EN_COMPTE, TRAITE }
}
