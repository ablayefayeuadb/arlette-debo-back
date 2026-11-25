package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Relais / délégation encadrée. Règles :
 * <ul>
 *   <li>activable uniquement après formation et validation des compétences ;</li>
 *   <li>la Coordinatrice agit par délégation de la Manager (pas de niveau hiérarchique parallèle).</li>
 * </ul>
 */
@Getter
@Entity
@Table(name = "td_delegation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delegation extends AuditableEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "beneficiaire_profil_code", nullable = false)
    private String beneficiaireProfilCode;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "formation_validee", nullable = false)
    private boolean formationValidee;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "activee_par")
    private UUID activeePar;

    @Column(name = "activee_le")
    private Instant activeeLe;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_delegation_perimetre", joinColumns = @JoinColumn(name = "delegation_id"))
    @Column(name = "perimetre_code", nullable = false)
    private Set<String> perimetres = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_delegation_action", joinColumns = @JoinColumn(name = "delegation_id"))
    private Set<DroitDelegue> droits = new HashSet<>();

    public Delegation(String code, String beneficiaire, String titre, String description, Set<String> perimetres,
            Set<DroitDelegue> droits, boolean formationValidee) {
        this.code = code;
        this.beneficiaireProfilCode = beneficiaire;
        this.titre = titre;
        this.description = description;
        this.perimetres.addAll(perimetres);
        this.droits.addAll(droits);
        this.formationValidee = formationValidee;
    }

    public void activer(UUID par, Instant le) {
        if (!formationValidee) {
            throw DomainException.regle("FORMATION_REQUISE",
                    "Relais activable uniquement après formation et validation des compétences.");
        }
        this.active = true;
        this.activeePar = par;
        this.activeeLe = le;
    }

    public void desactiver() {
        this.active = false;
    }

    /** Retirer la validation de formation désactive le relais. */
    public void definirFormation(boolean validee) {
        this.formationValidee = validee;
        if (!validee) {
            this.active = false;
        }
    }

    @Getter
    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class DroitDelegue {

        @Column(name = "module_code", nullable = false)
        private String moduleCode;

        @Column(name = "action_code", length = 1, nullable = false)
        @JdbcTypeCode(SqlTypes.CHAR)
        private String actionCode;

        public DroitDelegue(String moduleCode, String actionCode) {
            this.moduleCode = moduleCode;
            this.actionCode = actionCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DroitDelegue d && d.moduleCode.equals(moduleCode) && d.actionCode.equals(actionCode);
        }

        @Override
        public int hashCode() {
            return 31 * moduleCode.hashCode() + actionCode.hashCode();
        }
    }
}
