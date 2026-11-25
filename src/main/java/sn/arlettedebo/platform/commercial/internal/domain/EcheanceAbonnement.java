package sn.arlettedebo.platform.commercial.internal.domain;

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

/** Paiement attendu d'un abonnement pour un mois donné (reconduit à chaque séance du mois suivant). */
@Getter
@Entity
@Table(name = "td_echeance_abonnement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EcheanceAbonnement extends AuditableEntity {

    @Column(name = "dossier_id", nullable = false, updatable = false)
    private UUID dossierId;

    @Column(name = "abonnement_id")
    private UUID abonnementId;

    /** Mois concerné, au format {@code AAAA-MM}. */
    @Column(name = "periode", nullable = false, updatable = false)
    private String periode;

    @Column(name = "echeance_le", nullable = false)
    private LocalDate echeanceLe;

    @Column(name = "montant", nullable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.ATTENDUE;

    /** Facture caisse liée (renseignée plus tard, facultatif). */
    @Column(name = "facture_id")
    private UUID factureId;

    @Column(name = "seance_suivi_id")
    private UUID seanceSuiviId;

    @Column(name = "payee_le")
    private Instant payeeLe;

    @Column(name = "payee_par")
    private UUID payeePar;

    public EcheanceAbonnement(UUID dossierId, UUID abonnementId, LocalDate echeanceLe, long montant, UUID seanceSuiviId) {
        this.dossierId = dossierId;
        this.abonnementId = abonnementId;
        this.echeanceLe = echeanceLe;
        this.periode = periodeDe(echeanceLe);
        this.montant = montant;
        this.seanceSuiviId = seanceSuiviId;
    }

    public static String periodeDe(LocalDate jour) {
        return "%04d-%02d".formatted(jour.getYear(), jour.getMonthValue());
    }

    public void payer(Instant quand, UUID par) {
        exigerAttendue();
        this.statut = Statut.PAYEE;
        this.payeeLe = quand;
        this.payeePar = par;
    }

    public void annuler() {
        exigerAttendue();
        this.statut = Statut.ANNULEE;
    }

    /** Montant ajustable tant que l'échéance n'est pas réglée. */
    public void changerMontant(long nouveau) {
        exigerAttendue();
        if (nouveau < 0) {
            throw DomainException.invalide("Le montant ne peut pas être négatif.");
        }
        this.montant = nouveau;
    }

    private void exigerAttendue() {
        if (statut != Statut.ATTENDUE) {
            throw DomainException.etatInvalide("Cette échéance est déjà clôturée (" + statut.name() + ").");
        }
    }

    public enum Statut { ATTENDUE, PAYEE, ANNULEE }
}
