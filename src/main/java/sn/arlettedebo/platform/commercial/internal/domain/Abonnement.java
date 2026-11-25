package sn.arlettedebo.platform.commercial.internal.domain;

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

@Getter
@Entity
@Table(name = "td_abonnement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Abonnement extends AuditableEntity {

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "formule_code", nullable = false, updatable = false)
    private String formuleCode;

    @Column(name = "debut", nullable = false)
    private LocalDate debut;

    @Column(name = "fin", nullable = false)
    private LocalDate fin;

    @Column(name = "seances_restantes")
    private Short seancesRestantes;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.ACTIF;

    @Column(name = "vendu_par", nullable = false, updatable = false)
    private UUID venduPar;

    public Abonnement(UUID clientId, String formule, LocalDate debut, int dureeJours, Short seances, UUID venduPar) {
        this.clientId = clientId;
        this.formuleCode = formule;
        this.debut = debut;
        this.fin = debut.plusDays(dureeJours - 1L);
        this.seancesRestantes = seances;
        this.venduPar = venduPar;
    }

    /** Consomme une séance (formules à séances). */
    public void consommer(LocalDate jour) {
        if (statut != Statut.ACTIF || jour.isAfter(fin)) {
            throw DomainException.etatInvalide("Abonnement inactif ou expiré.");
        }
        if (seancesRestantes != null) {
            if (seancesRestantes <= 0) {
                throw DomainException.etatInvalide("Plus aucune séance disponible.");
            }
            seancesRestantes = (short) (seancesRestantes - 1);
        }
    }

    public void changerStatut(Statut cible) {
        statut = cible;
    }

    public enum Statut { ACTIF, SUSPENDU, EXPIRE, RESILIE }
}
