package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Action corrective : à faire → faite (responsable) → vérifiée (autre personne). */
@Getter
@Entity
@Table(name = "td_action_corrective")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionCorrective extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "non_conformite_id", nullable = false, updatable = false)
    private NonConformite nonConformite;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "responsable_id", nullable = false)
    private UUID responsableId;

    @Column(name = "echeance", nullable = false)
    private LocalDate echeance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.A_FAIRE;

    @Column(name = "verifiee_par")
    private UUID verifieePar;

    @Column(name = "verifiee_le")
    private Instant verifieeLe;

    ActionCorrective(NonConformite nc, String libelle, UUID responsable, LocalDate echeance) {
        this.nonConformite = nc;
        this.libelle = libelle;
        this.responsableId = responsable;
        this.echeance = echeance;
    }

    public void realiser() {
        if (statut != Statut.A_FAIRE) {
            throw DomainException.etatInvalide("Action déjà réalisée.");
        }
        statut = Statut.FAITE;
    }

    public void verifier(UUID par, Instant le) {
        if (statut != Statut.FAITE) {
            throw DomainException.etatInvalide("Seule une action réalisée peut être vérifiée.");
        }
        if (Objects.equals(par, responsableId)) {
            throw DomainException.regle("SEPARATION_DES_TACHES", "Le responsable d'une action ne vérifie pas sa propre action.");
        }
        statut = Statut.VERIFIEE;
        verifieePar = par;
        verifieeLe = le;
    }

    public enum Statut { A_FAIRE, FAITE, VERIFIEE }
}
