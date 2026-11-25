package sn.arlettedebo.platform.stock.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Article (consommable, produit boutique, matériel). Le stock ne bouge que par mouvement tracé. */
@Getter
@Entity
@Table(name = "td_article")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends AuditableEntity {

    @Column(name = "reference", nullable = false, updatable = false)
    private String reference;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "categorie_code", nullable = false)
    private String categorieCode;

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "unite", nullable = false)
    private String unite;

    @Column(name = "stock_actuel", nullable = false)
    private int stockActuel;

    @Column(name = "seuil", nullable = false)
    private int seuil;

    @Column(name = "prix_achat", columnDefinition = "numeric(12,0)")
    private Long prixAchat;

    @Column(name = "prix_vente", columnDefinition = "numeric(12,0)")
    private Long prixVente;

    @Column(name = "fournisseur")
    private String fournisseur;

    /** Gamme du produit boutique (MASQUES, CHANTILLY…) ; null pour un consommable. */
    @Column(name = "type_produit", length = 40)
    private String typeProduit;

    @Column(name = "dernier_appro_le")
    private LocalDate dernierApproLe;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Fiche article (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String reference, String nom, String categorie, String perimetre, String unite, int seuil,
            Long prixAchat, Long prixVente, String fournisseur, String typeProduit) {
    }

    public Article(Donnees d) {
        this.reference = d.reference();
        this.nom = d.nom();
        this.categorieCode = d.categorie();
        this.perimetreCode = d.perimetre();
        this.unite = d.unite();
        this.seuil = d.seuil();
        this.prixAchat = d.prixAchat();
        this.prixVente = d.prixVente();
        this.fournisseur = d.fournisseur();
        this.typeProduit = d.typeProduit();
    }

    /** Applique un mouvement signé ; le stock ne peut pas devenir négatif. */
    void appliquer(int delta, LocalDate jour, boolean approvisionnement) {
        if (stockActuel + delta < 0) {
            throw DomainException.regle("STOCK_INSUFFISANT", "Stock insuffisant pour " + nom + " (" + stockActuel + " " + unite + ").");
        }
        stockActuel += delta;
        if (approvisionnement) {
            dernierApproLe = jour;
        }
    }

    /** Paramétrage du catalogue (seuil d'alerte, prix, fournisseur, actif) ; le stock ne change que par mouvement. */
    public void parametrer(int seuil, Long prixAchat, Long prixVente, String fournisseur, boolean actif) {
        this.seuil = seuil;
        this.prixAchat = prixAchat;
        this.prixVente = prixVente;
        this.fournisseur = fournisseur;
        this.actif = actif;
    }

    public boolean enAlerte() {
        return stockActuel <= seuil;
    }
}
