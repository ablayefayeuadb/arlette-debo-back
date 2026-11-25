package sn.arlettedebo.platform.gouvernance.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Incident de responsabilité / conflit de consignes (ORG-03) — distinct des tickets maintenance. */
@Getter
@Entity
@Table(name = "td_incident_responsabilite")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentResponsabilite extends AuditableEntity {

    @Column(name = "sujet", nullable = false)
    private String sujet;

    @Column(name = "roles_concernes", nullable = false)
    private String rolesConcernes;

    @Column(name = "personnes")
    private String personnes;

    @Column(name = "arbitrage")
    private String arbitrage;

    @Column(name = "action_corrective")
    private String actionCorrective;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "echeance")
    private LocalDate echeance;

    @Column(name = "responsable_id")
    private UUID responsableId;

    @Column(name = "responsable_nom")
    private String responsableNom;

    @Column(name = "rendez_vous_id")
    private UUID rendezVousId;

    @Column(name = "cloture_le")
    private Instant clotureLe;

    public IncidentResponsabilite(String sujet, String rolesConcernes, String personnes, LocalDate echeance,
            UUID responsableId, String responsableNom, UUID rendezVousId) {
        this.sujet = exiger(sujet, "Le sujet est obligatoire.");
        this.rolesConcernes = exiger(rolesConcernes, "Les rôles concernés sont obligatoires.");
        this.personnes = blankToNull(personnes);
        this.echeance = echeance;
        this.responsableId = responsableId;
        this.responsableNom = blankToNull(responsableNom);
        this.rendezVousId = rendezVousId;
        this.statut = Statut.OUVERT;
    }

    public void mettreAJour(String arbitrage, String actionCorrective, LocalDate echeance, String responsableNom) {
        if (statut == Statut.CLOTURE) {
            throw DomainException.etatInvalide("Incident déjà clôturé.");
        }
        this.arbitrage = blankToNull(arbitrage);
        this.actionCorrective = blankToNull(actionCorrective);
        this.echeance = echeance;
        if (responsableNom != null) {
            this.responsableNom = blankToNull(responsableNom);
        }
        if (statut == Statut.OUVERT && (this.arbitrage != null || this.actionCorrective != null)) {
            this.statut = Statut.EN_COURS;
        }
    }

    public void cloturer(Instant maintenant) {
        if (statut == Statut.CLOTURE) {
            throw DomainException.etatInvalide("Incident déjà clôturé.");
        }
        if (arbitrage == null || arbitrage.isBlank()) {
            throw DomainException.invalide("L'arbitrage est requis pour clôturer.");
        }
        this.statut = Statut.CLOTURE;
        this.clotureLe = maintenant;
    }

    private static String exiger(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw DomainException.invalide(msg);
        }
        return v.trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public enum Statut {
        OUVERT, EN_COURS, CLOTURE
    }
}
