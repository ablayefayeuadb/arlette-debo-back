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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Non-conformité et son plan d'actions correctives (agrégat). */
@Getter
@Entity
@Table(name = "td_non_conformite")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NonConformite extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "date_constat", nullable = false)
    private LocalDate dateConstat;

    @Enumerated(EnumType.STRING)
    @Column(name = "origine", nullable = false)
    private Origine origine;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravite", nullable = false)
    private Gravite gravite;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "controle_id")
    private UUID controleId;

    @Column(name = "cree_par_id", nullable = false, updatable = false)
    private UUID creeParId;

    @Column(name = "cloturee_par")
    private UUID clotureePar;

    @Column(name = "cloturee_le")
    private Instant clotureeLe;

    @BatchSize(size = 32)
    @OrderBy("echeance asc")
    @OneToMany(mappedBy = "nonConformite", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActionCorrective> actions = new ArrayList<>();

    public NonConformite(String perimetre, LocalDate date, Origine origine, String description, Gravite gravite, UUID controleId, UUID creePar) {
        this.perimetreCode = perimetre;
        this.dateConstat = date;
        this.origine = origine;
        this.description = description;
        this.gravite = gravite;
        this.controleId = controleId;
        this.creeParId = creePar;
    }

    public ActionCorrective planifier(String libelle, UUID responsable, LocalDate echeance) {
        if (statut == Statut.CLOTUREE) {
            throw DomainException.etatInvalide("Non-conformité clôturée.");
        }
        ActionCorrective a = new ActionCorrective(this, libelle, responsable, echeance);
        actions.add(a);
        statut = Statut.ACTION_EN_COURS;
        return a;
    }

    /** Clôture possible uniquement quand toutes les actions sont vérifiées. */
    public void cloturer(UUID par, Instant le) {
        if (actions.isEmpty() || actions.stream().anyMatch(a -> a.getStatut() != ActionCorrective.Statut.VERIFIEE)) {
            throw DomainException.regle("ACTIONS_NON_VERIFIEES", "Toutes les actions correctives doivent être vérifiées avant clôture.");
        }
        statut = Statut.CLOTUREE;
        clotureePar = par;
        clotureeLe = le;
    }

    public enum Origine { CONTROLE, RECLAMATION, HYGIENE, SIGNALEMENT, AUDIT }

    public enum Gravite { MINEURE, MAJEURE, CRITIQUE }

    public enum Statut { OUVERTE, ACTION_EN_COURS, CLOTUREE }
}
