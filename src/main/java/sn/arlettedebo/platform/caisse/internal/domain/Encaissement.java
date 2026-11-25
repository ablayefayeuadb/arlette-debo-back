package sn.arlettedebo.platform.caisse.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Encaissement — jamais supprimé : une erreur s'annule (traçabilité). */
@Getter
@Entity
@Table(name = "td_encaissement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encaissement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SessionCaisse session;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "rendez_vous_id")
    private UUID rendezVousId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "coiffeuse_id")
    private UUID coiffeuseId;

    @Column(name = "demande_autorisation_id")
    private UUID demandeAutorisationId;

    @Column(name = "facture_id")
    private UUID factureId;

    @Column(name = "montant", nullable = false, updatable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Column(name = "mode_paiement_code", nullable = false)
    private String modePaiementCode;

    @Column(name = "encaisse_par", nullable = false, updatable = false)
    private UUID encaissePar;

    @Column(name = "note")
    private String note;

    @Column(name = "annule", nullable = false)
    private boolean annule;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "cree_par", updatable = false)
    private UUID creePar;

    Encaissement(SessionCaisse session, String libelle, long montant, String modePaiement, UUID par, Instant le) {
        if (montant <= 0) {
            throw DomainException.invalide("Le montant encaissé doit être positif.");
        }
        this.session = session;
        this.libelle = libelle;
        this.montant = montant;
        this.modePaiementCode = modePaiement;
        this.encaissePar = par;
        this.horodatage = le;
        this.creeLe = le;
        this.creePar = par;
    }

    public void rattacher(UUID rendezVousId, UUID clientId, UUID coiffeuseId, UUID demandeAutorisationId) {
        this.rendezVousId = rendezVousId;
        this.clientId = clientId;
        this.coiffeuseId = coiffeuseId;
        this.demandeAutorisationId = demandeAutorisationId;
    }

    public void lierFacture(UUID factureId) {
        this.factureId = factureId;
    }

    /** Note libre (référence de transaction mobile, précision…). */
    public void annoter(String texte) {
        this.note = texte == null || texte.isBlank() ? null : texte.trim();
    }

    public void annuler(String motif) {
        if (annule) {
            throw DomainException.etatInvalide("Encaissement déjà annulé.");
        }
        annule = true;
        note = note == null ? "Annulé : " + motif : note + " · annulé : " + motif;
    }
}
