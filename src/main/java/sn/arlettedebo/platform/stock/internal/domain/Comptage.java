package sn.arlettedebo.platform.stock.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Comptage d'inventaire. Règle « Gestion des stocks » : l'assistant saisit et signale l'écart,
 * il ne corrige pas seul le stock — l'ajustement est décidé par une autre personne habilitée.
 */
@Getter
@Entity
@Table(name = "td_comptage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comptage extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "article_id", nullable = false, updatable = false)
    private UUID articleId;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @Column(name = "theorique", nullable = false, updatable = false)
    private int theorique;

    @Column(name = "compte", nullable = false, updatable = false)
    private int compte;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut;

    @Column(name = "saisi_par", nullable = false, updatable = false)
    private UUID saisiPar;

    @Column(name = "traite_par")
    private UUID traitePar;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @Column(name = "commentaire")
    private String commentaire;

    public Comptage(Article article, int compte, UUID saisiPar, Instant le) {
        if (compte < 0) {
            throw DomainException.invalide("Quantité comptée négative.");
        }
        this.articleId = article.getId();
        this.theorique = article.getStockActuel();
        this.compte = compte;
        this.saisiPar = saisiPar;
        this.horodatage = le;
        this.statut = compte == theorique ? Statut.CONFORME : Statut.ECART_SIGNALE;
    }

    /** Écart = compté − théorique (colonne générée en base). */
    public int ecart() {
        return compte - theorique;
    }

    public void commenter(String texte) {
        this.commentaire = texte;
    }

    public void traiter(boolean ajuster, UUID par, Instant le, String commentaire) {
        if (statut != Statut.ECART_SIGNALE) {
            throw DomainException.etatInvalide("Aucun écart à traiter.");
        }
        if (Objects.equals(par, saisiPar)) {
            throw DomainException.regle("SEPARATION_DES_TACHES", "La personne qui compte ne corrige pas seule le stock.");
        }
        statut = ajuster ? Statut.AJUSTE : Statut.REJETE;
        traitePar = par;
        traiteLe = le;
        this.commentaire = commentaire;
    }

    public enum Statut { CONFORME, ECART_SIGNALE, AJUSTE, REJETE }
}
