package sn.arlettedebo.platform.stock.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Commande fournisseur : besoin → validée (DO / DG, pas le demandeur) → commandée → reçue. */
@Getter
@Entity
@Table(name = "td_commande")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Commande extends AuditableEntity {

    @Column(name = "reference", nullable = false, updatable = false)
    private String reference;

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "fournisseur", nullable = false)
    private String fournisseur;

    @Column(name = "montant_estime", nullable = false, columnDefinition = "numeric(14,0)")
    private long montantEstime;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.BESOIN;

    @Column(name = "cree_par_id", nullable = false, updatable = false)
    private UUID creeParId;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "passee_le")
    private Instant passeeLe;

    @Column(name = "recue_par")
    private UUID recuePar;

    @Column(name = "recue_le")
    private Instant recueLe;

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommande> lignes = new ArrayList<>();

    public Commande(String reference, String perimetre, String fournisseur, UUID creePar) {
        this.reference = reference;
        this.perimetreCode = perimetre;
        this.fournisseur = fournisseur;
        this.creeParId = creePar;
    }

    public void ajouterLigne(Article article, int quantite) {
        if (statut != Statut.BESOIN) {
            throw DomainException.etatInvalide("Commande déjà validée.");
        }
        long prix = article.getPrixAchat() == null ? 0 : article.getPrixAchat();
        lignes.add(new LigneCommande(this, article.getId(), quantite, prix));
        montantEstime = lignes.stream().mapToLong(l -> l.getPrixUnitaire() * l.getQuantite()).sum();
    }

    public void decider(boolean valider, UUID par, Instant le) {
        exiger(Statut.BESOIN);
        if (Objects.equals(par, creeParId)) {
            throw DomainException.regle("SEPARATION_DES_TACHES", "La validation revient à la DO / DG, distincte du demandeur.");
        }
        statut = valider ? Statut.VALIDEE : Statut.REFUSEE;
        validePar = par;
        valideLe = le;
    }

    public void passer(Instant le) {
        exiger(Statut.VALIDEE);
        statut = Statut.COMMANDEE;
        passeeLe = le;
    }

    public void receptionner(UUID par, Instant le) {
        exiger(Statut.COMMANDEE);
        statut = Statut.RECUE;
        recuePar = par;
        recueLe = le;
    }

    private void exiger(Statut attendu) {
        if (statut != attendu) {
            throw DomainException.etatInvalide("Étape impossible : commande au statut " + statut + ".");
        }
    }

    public enum Statut { BESOIN, VALIDEE, REFUSEE, COMMANDEE, RECUE }
}
