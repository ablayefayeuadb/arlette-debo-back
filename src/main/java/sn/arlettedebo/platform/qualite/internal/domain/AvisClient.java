package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Avis client (note 1–5) — immuable une fois enregistré. */
@Getter
@Entity
@Table(name = "td_avis_client")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvisClient extends BaseEntity {

    @Column(name = "perimetre_code", nullable = false, updatable = false)
    private String perimetreCode;

    @Column(name = "date_avis", nullable = false, updatable = false)
    private Instant dateAvis;

    @Column(name = "client_id", updatable = false)
    private UUID clientId;

    @Column(name = "cliente_nom", nullable = false, updatable = false)
    private String clienteNom;

    @Column(name = "rendez_vous_id", updatable = false)
    private UUID rendezVousId;

    @Column(name = "note", nullable = false, updatable = false)
    private short note;

    @Column(name = "commentaire", updatable = false)
    private String commentaire;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "cree_par", updatable = false)
    private UUID creePar;

    /** Avis client (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(String perimetre, Instant date, UUID clientId, String clienteNom, UUID rendezVousId,
            int note, String commentaire, UUID creePar) {
    }

    public AvisClient(Donnees d) {
        if (d.note() < 1 || d.note() > 5) {
            throw DomainException.invalide("La d.note() va de 1 à 5.");
        }
        this.perimetreCode = d.perimetre();
        this.dateAvis = d.date();
        this.clientId = d.clientId();
        this.clienteNom = d.clienteNom();
        this.rendezVousId = d.rendezVousId();
        this.note = (short) d.note();
        this.commentaire = d.commentaire();
        this.creeLe = d.date();
        this.creePar = d.creePar();
    }
}
