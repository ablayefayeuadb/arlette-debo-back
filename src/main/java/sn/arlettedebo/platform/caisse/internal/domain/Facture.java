package sn.arlettedebo.platform.caisse.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Facture présentée au caissier : émise par la boutique (produits) ou à la fin d'une prestation.
 * Le paiement crée un encaissement sur la caisse du salon.
 */
@Getter
@Entity
@Table(name = "td_facture")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S2160") // égalité JPA via BaseEntity / AuditableEntity (proxy Hibernate)
public class Facture extends AuditableEntity {

    @Column(name = "numero", nullable = false, updatable = false)
    private String numero;

    @Column(name = "perimetre_origine", nullable = false, updatable = false)
    private String perimetreOrigine;

    @Column(name = "jour", nullable = false, updatable = false)
    private LocalDate jour;

    @Enumerated(EnumType.STRING)
    @Column(name = "origine", nullable = false, updatable = false)
    private Origine origine;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "cliente_nom")
    private String clienteNom;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "telephone", columnDefinition = "text")
    private String telephone;

    @Column(name = "montant", nullable = false, updatable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Column(name = "libelle", nullable = false, updatable = false)
    private String libelle;

    @Column(name = "rendez_vous_id", updatable = false)
    private UUID rendezVousId;

    @Column(name = "encaissement_id")
    private UUID encaissementId;

    @Column(name = "payee_le")
    private Instant payeeLe;

    @Column(name = "motif_annulation")
    private String motifAnnulation;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par")
    private UUID annuleePar;

    @OneToMany(mappedBy = "facture", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneFacture> lignes = new ArrayList<>();

    @SuppressWarnings("java:S107") // champs métier obligatoires à la création (pas de builder partiel)
    public Facture(String numero, String perimetreOrigine, LocalDate jour, Origine origine, String libelle, long montant,
            UUID clientId, String clienteNom, String telephone, UUID rendezVousId) {
        if (montant <= 0) {
            throw DomainException.invalide("Le montant de la facture doit être positif.");
        }
        this.numero = numero;
        this.perimetreOrigine = perimetreOrigine;
        this.jour = jour;
        this.origine = origine;
        this.statut = Statut.IMPAYEE;
        this.libelle = libelle;
        this.montant = montant;
        this.clientId = clientId;
        this.clienteNom = clienteNom == null || clienteNom.isBlank() ? null : clienteNom.trim();
        this.telephone = telephone == null || telephone.isBlank() ? null : telephone.trim();
        this.rendezVousId = rendezVousId;
    }

    public void ajouterLigne(UUID articleId, String libelleLigne, int quantite, long prixUnitaire) {
        lignes.add(new LigneFacture(this, articleId, libelleLigne, quantite, prixUnitaire));
    }

    public void payer(UUID encaissementId, Instant le) {
        if (statut != Statut.IMPAYEE) {
            throw DomainException.etatInvalide("Cette facture n'est plus à encaisser.");
        }
        this.statut = Statut.PAYEE;
        this.encaissementId = encaissementId;
        this.payeeLe = le;
    }

    public void annuler() {
        if (statut == Statut.PAYEE) {
            throw DomainException.etatInvalide("Une facture déjà payée ne peut pas être annulée.");
        }
        this.statut = Statut.ANNULEE;
    }

    /**
     * Annulation motivée d'une facture émise, impayée ou payée (l'encaissement lié est annulé par l'appelant).
     * Déjà annulée ⇒ 409.
     */
    public void annulerEmise(String motif, UUID par, Instant le) {
        if (statut == Statut.ANNULEE) {
            throw DomainException.etatInvalide("Cette facture est déjà annulée.");
        }
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("Le motif d'annulation est obligatoire.");
        }
        this.statut = Statut.ANNULEE;
        this.motifAnnulation = motif.trim();
        this.annuleePar = par;
        this.annuleeLe = le;
    }

    /** Remet la facture à encaisser après annulation de l'encaissement lié. */
    public void reouvrirApresAnnulationEncaissement() {
        if (statut != Statut.PAYEE) {
            throw DomainException.etatInvalide("Seule une facture payée peut être rouverte.");
        }
        this.statut = Statut.IMPAYEE;
        this.encaissementId = null;
        this.payeeLe = null;
    }

    public enum Origine {
        BOUTIQUE, PRESTATION
    }

    public enum Statut {
        IMPAYEE, PAYEE, ANNULEE
    }
}
