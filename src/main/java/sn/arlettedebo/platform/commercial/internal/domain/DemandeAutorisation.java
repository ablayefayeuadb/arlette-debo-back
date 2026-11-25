package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import sn.arlettedebo.platform.shared.crypto.EncryptedStringConverter;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Règle « Décisions commerciales » : remises, gratuités, remboursements et compensations
 * exceptionnelles nécessitent une autorisation (DG ou DO), jamais celle du demandeur.
 */
@Getter
@Entity
@Table(name = "td_demande_autorisation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandeAutorisation extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false, updatable = false)
    private String perimetreCode;

    @Column(name = "type_code", nullable = false, updatable = false)
    private String typeCode;

    @Column(name = "client_id", updatable = false)
    private UUID clientId;

    @Column(name = "cliente_nom", nullable = false, updatable = false)
    private String clienteNom;

    @Column(name = "montant", nullable = false, updatable = false, columnDefinition = "numeric(12,0)")
    private long montant;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "motif", nullable = false, updatable = false, columnDefinition = "text")
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "demande_par", nullable = false, updatable = false)
    private UUID demandePar;

    @Column(name = "demande_le", nullable = false, updatable = false)
    private Instant demandeLe;

    @Column(name = "decide_par")
    private UUID decidePar;

    @Column(name = "decide_le")
    private Instant decideLe;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "appliquee_le")
    private Instant appliqueeLe;

    /** Demande de geste commercial (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, String type, UUID clientId, String clienteNom, long montant, String motif,
            UUID par, Instant le) {
    }

    public DemandeAutorisation(Donnees d) {
        this.perimetreCode = d.perimetre();
        this.typeCode = d.type();
        this.clientId = d.clientId();
        this.clienteNom = d.clienteNom();
        this.montant = d.montant();
        this.motif = d.motif();
        this.demandePar = d.par();
        this.demandeLe = d.le();
    }

    public void decider(boolean autoriser, UUID par, Instant le, String commentaire) {
        if (statut != Statut.EN_ATTENTE) {
            throw DomainException.etatInvalide("Demande déjà traitée.");
        }
        if (Objects.equals(par, demandePar)) {
            throw DomainException.regle("SEPARATION_DES_TACHES", "Une autre personne (DG / DO) doit autoriser cette demande.");
        }
        statut = autoriser ? Statut.AUTORISEE : Statut.REFUSEE;
        decidePar = par;
        decideLe = le;
        this.commentaire = commentaire;
    }

    public void appliquer(Instant le) {
        if (statut != Statut.AUTORISEE) {
            throw DomainException.etatInvalide("Seule une décision autorisée peut être appliquée.");
        }
        statut = Statut.APPLIQUEE;
        appliqueeLe = le;
    }

    public enum Statut { EN_ATTENTE, AUTORISEE, REFUSEE, APPLIQUEE }
}
