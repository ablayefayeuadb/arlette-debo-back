package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

@Getter
@Entity
@Table(name = "td_reclamation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reclamation extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "date_reclamation", nullable = false)
    private Instant dateReclamation;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "cliente_nom", nullable = false)
    private String clienteNom;

    @Column(name = "rendez_vous_id")
    private UUID rendezVousId;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false)
    private Canal canal;

    @Column(name = "motif", nullable = false)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravite", nullable = false)
    private NonConformite.Gravite gravite;

    @Column(name = "action_immediate")
    private String actionImmediate;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.NOUVELLE;

    @Column(name = "reponse")
    private String reponse;

    @Column(name = "traite_par")
    private UUID traitePar;

    @Column(name = "cloturee_le")
    private Instant clotureeLe;

    /** Réclamation reçue (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, Instant date, UUID clientId, String clienteNom, UUID rendezVousId,
            Canal canal, String motif, NonConformite.Gravite gravite, String actionImmediate) {
    }

    public Reclamation(Donnees d) {
        this.perimetreCode = d.perimetre();
        this.dateReclamation = d.date();
        this.clientId = d.clientId();
        this.clienteNom = d.clienteNom();
        this.rendezVousId = d.rendezVousId();
        this.canal = d.canal();
        this.motif = d.motif();
        this.gravite = d.gravite();
        this.actionImmediate = d.actionImmediate();
    }

    public void prendreEnCharge(UUID par) {
        if (statut != Statut.NOUVELLE) {
            throw DomainException.etatInvalide("Réclamation déjà prise en charge.");
        }
        statut = Statut.EN_TRAITEMENT;
        traitePar = par;
    }

    public void resoudre(UUID par, String reponse, Instant le) {
        if (statut == Statut.RESOLUE) {
            throw DomainException.etatInvalide("Réclamation déjà résolue.");
        }
        statut = Statut.RESOLUE;
        traitePar = par;
        this.reponse = reponse;
        clotureeLe = le;
    }

    public enum Canal { ACCUEIL, TELEPHONE, WHATSAPP, RESEAUX }

    public enum Statut { NOUVELLE, EN_TRAITEMENT, RESOLUE }
}
