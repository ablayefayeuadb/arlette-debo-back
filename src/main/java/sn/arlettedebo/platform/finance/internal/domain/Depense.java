package sn.arlettedebo.platform.finance.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Dépense : demande → validation (ou refus) → paiement → contrôle.
 * <p>Règle « Séparation des tâches financières » : personne ne cumule seul la chaîne.
 * <ul>
 *   <li>le valideur n'est pas le demandeur ;</li>
 *   <li>le payeur n'est pas le valideur ;</li>
 *   <li>le contrôleur n'est intervenu à aucune étape précédente.</li>
 * </ul>
 * Ces invariants sont vérifiés ici ET par des contraintes CHECK en base (défense en profondeur).
 */
@Getter
@Entity
@Table(name = "td_depense")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Depense extends AuditableEntity {

    @Column(name = "reference", nullable = false, updatable = false)
    private String reference;

    @Column(name = "objet", nullable = false)
    private String objet;

    @Column(name = "categorie_code", nullable = false)
    private String categorieCode;

    /** Nul = dépense transverse. */
    @Column(name = "perimetre_code")
    private String perimetreCode;

    @Column(name = "montant", nullable = false, columnDefinition = "numeric(14,0)")
    private long montant;

    @Column(name = "fournisseur", nullable = false)
    private String fournisseur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "demande_par", nullable = false, updatable = false)
    private UUID demandePar;

    @Column(name = "demande_le", nullable = false, updatable = false)
    private Instant demandeLe;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "motif_refus")
    private String motifRefus;

    @Column(name = "paye_par")
    private UUID payePar;

    @Column(name = "paye_le")
    private Instant payeLe;

    @Column(name = "mode_paiement_code")
    private String modePaiementCode;

    @Column(name = "controle_par")
    private UUID controlePar;

    @Column(name = "controle_le")
    private Instant controleLe;

    @Column(name = "commentaire")
    private String commentaire;

    /** Demande de dépense (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String reference, String objet, String categorie, String perimetre, long montant,
            String fournisseur, UUID demandePar, Instant le) {
    }

    public Depense(Donnees d) {
        if (d.montant() <= 0) {
            throw DomainException.invalide("Le d.montant() doit être positif.");
        }
        this.reference = d.reference();
        this.objet = d.objet();
        this.categorieCode = d.categorie();
        this.perimetreCode = d.perimetre();
        this.montant = d.montant();
        this.fournisseur = d.fournisseur();
        this.demandePar = d.demandePar();
        this.demandeLe = d.le();
        this.statut = Statut.DEMANDEE;
    }

    public void valider(UUID par, Instant le) {
        exigerStatut(Statut.DEMANDEE, "valider");
        if (Objects.equals(par, demandePar)) {
            throw separation("Le demandeur ne peut pas valider sa propre demande.");
        }
        statut = Statut.VALIDEE;
        validePar = par;
        valideLe = le;
    }

    public void refuser(UUID par, Instant le, String motif) {
        exigerStatut(Statut.DEMANDEE, "refuser");
        if (Objects.equals(par, demandePar)) {
            throw separation("Le demandeur ne peut pas statuer sur sa propre demande.");
        }
        statut = Statut.REFUSEE;
        validePar = par;
        valideLe = le;
        motifRefus = motif;
    }

    public void payer(UUID par, Instant le, String modePaiement) {
        exigerStatut(Statut.VALIDEE, "payer");
        if (Objects.equals(par, validePar)) {
            throw separation("La personne qui a validé ne peut pas exécuter le paiement.");
        }
        statut = Statut.PAYEE;
        payePar = par;
        payeLe = le;
        modePaiementCode = modePaiement;
    }

    public void controler(UUID par, Instant le, boolean justificatifPresent, String commentaire) {
        exigerStatut(Statut.PAYEE, "contrôler");
        if (!justificatifPresent) {
            throw DomainException.regle("JUSTIFICATIF_MANQUANT", "Contrôle impossible : justificatif manquant.");
        }
        if (Objects.equals(par, demandePar) || Objects.equals(par, validePar) || Objects.equals(par, payePar)) {
            throw separation("Le contrôle doit être fait par une personne qui n'est intervenue à aucune étape.");
        }
        statut = Statut.CONTROLEE;
        controlePar = par;
        controleLe = le;
        this.commentaire = commentaire;
    }

    public boolean estIntervenant(UUID utilisateur) {
        return Objects.equals(utilisateur, demandePar) || Objects.equals(utilisateur, validePar) || Objects.equals(utilisateur, payePar);
    }

    private void exigerStatut(Statut attendu, String action) {
        if (statut != attendu) {
            throw DomainException.etatInvalide("Impossible de " + action + " une dépense au statut " + statut + ".");
        }
    }

    private static DomainException separation(String message) {
        return DomainException.regle("SEPARATION_DES_TACHES", "Séparation des tâches : " + message);
    }

    public enum Statut {
        DEMANDEE, VALIDEE, REFUSEE, PAYEE, CONTROLEE
    }
}
