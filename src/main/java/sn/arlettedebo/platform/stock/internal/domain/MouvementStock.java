package sn.arlettedebo.platform.stock.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Mouvement de stock (journal immuable) : entrée, sortie, vente, ajustement, transfert, perte, casse. */
@Getter
@Entity
@Table(name = "td_mouvement_stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MouvementStock extends BaseEntity {

    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private Type type;

    /** Signé : positif = entrée, négatif = sortie. */
    @Column(name = "quantite", nullable = false, updatable = false)
    private int quantite;

    @Column(name = "motif", updatable = false)
    private String motif;

    @Column(name = "effectue_par", nullable = false, updatable = false)
    private UUID effectuePar;

    @Column(name = "autorise_par", updatable = false)
    private UUID autorisePar;

    @Column(name = "encaissement_id", updatable = false)
    private UUID encaissementId;

    @Column(name = "comptage_id", updatable = false)
    private UUID comptageId;

    @Column(name = "commande_id", updatable = false)
    private UUID commandeId;

    private MouvementStock(Article article, Type type, int quantite, String motif, UUID par, UUID autorisePar, Instant le) {
        this.articleId = article.getId();
        this.type = type;
        this.quantite = quantite;
        this.motif = motif;
        this.effectuePar = par;
        this.autorisePar = autorisePar;
        this.horodatage = le;
    }

    /** Mouvement à appliquer (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(Article article, Type type, int quantite, String motif, UUID par, UUID autorisePar,
            Instant le, ZoneId zone) {
    }

    /** Crée le mouvement ET l'applique à l'article (un seul point d'entrée pour modifier un stock). */
    public static MouvementStock appliquer(Donnees d) {
        if (d.quantite() == 0) {
            throw DomainException.invalide("Quantité nulle.");
        }
        int signe = switch (d.type()) {
            case ENTREE -> 1;
            case SORTIE, VENTE, PERTE, CASSE -> -1;
            case AJUSTEMENT, TRANSFERT -> Integer.signum(d.quantite());
        };
        int delta = signe * Math.abs(d.quantite());
        d.article().appliquer(delta, LocalDate.ofInstant(d.le(), d.zone()), d.type() == Type.ENTREE);
        return new MouvementStock(d.article(), d.type(), delta, d.motif(), d.par(), d.autorisePar(), d.le());
    }

    public MouvementStock lier(UUID encaissement, UUID comptage, UUID commande) {
        this.encaissementId = encaissement;
        this.comptageId = comptage;
        this.commandeId = commande;
        return this;
    }

    public enum Type { ENTREE, SORTIE, VENTE, AJUSTEMENT, TRANSFERT, PERTE, CASSE }
}
