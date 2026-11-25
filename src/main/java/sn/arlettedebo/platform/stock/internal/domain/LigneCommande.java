package sn.arlettedebo.platform.stock.internal.domain;

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
@Table(name = "td_ligne_commande")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LigneCommande extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false, updatable = false)
    private Commande commande;

    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @Column(name = "quantite", nullable = false)
    private int quantite;

    @Column(name = "prix_unitaire", nullable = false, columnDefinition = "numeric(12,0)")
    private long prixUnitaire;

    LigneCommande(Commande commande, UUID articleId, int quantite, long prixUnitaire) {
        if (quantite <= 0) {
            throw DomainException.invalide("Quantité commandée positive requise.");
        }
        this.commande = commande;
        this.articleId = articleId;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
    }
}
