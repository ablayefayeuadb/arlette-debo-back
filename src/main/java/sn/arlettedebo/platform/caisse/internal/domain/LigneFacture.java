package sn.arlettedebo.platform.caisse.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

@Getter
@Entity
@Table(name = "td_ligne_facture")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S2160") // égalité JPA via BaseEntity (proxy Hibernate)
public class LigneFacture extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false)
    private Facture facture;

    @Column(name = "article_id")
    private UUID articleId;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "quantite", nullable = false)
    private short quantite;

    @Column(name = "prix_unitaire", nullable = false, columnDefinition = "numeric(12,0)")
    private long prixUnitaire;

    @Column(name = "montant", nullable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    LigneFacture(Facture facture, UUID articleId, String libelle, int quantite, long prixUnitaire) {
        if (quantite < 1 || quantite > 999) {
            throw DomainException.invalide("Quantité invalide.");
        }
        if (prixUnitaire < 0) {
            throw DomainException.invalide("Prix unitaire invalide.");
        }
        this.facture = facture;
        this.articleId = articleId;
        this.libelle = libelle.trim();
        this.quantite = (short) quantite;
        this.prixUnitaire = prixUnitaire;
        this.montant = prixUnitaire * quantite;
    }
}
