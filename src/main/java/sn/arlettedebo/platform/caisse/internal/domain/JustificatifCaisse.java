package sn.arlettedebo.platform.caisse.internal.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.BaseEntity;

/**
 * Pièce justificative de caisse (ticket TPE, reçu Wave / Orange Money, bordereau de remise en banque,
 * fiche de comptage). Jamais modifiée ; retirable par la personne qui l'a déposée tant que la caisse est ouverte.
 */
@Getter
@Entity
@Table(name = "td_justificatif_caisse")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S2160") // égalité JPA via BaseEntity (proxy Hibernate)
public class JustificatifCaisse extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "encaissement_id", updatable = false)
    private UUID encaissementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, updatable = false)
    private Nature nature;

    @Column(name = "commentaire", updatable = false)
    private String commentaire;

    @Column(name = "nom_fichier", nullable = false, updatable = false)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false, updatable = false)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false, updatable = false)
    private long tailleOctets;

    @Column(name = "empreinte_sha256", nullable = false, updatable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String empreinteSha256;

    @Column(name = "chemin_stockage", nullable = false, updatable = false)
    private String cheminStockage;

    @Column(name = "depose_par", nullable = false, updatable = false)
    private UUID deposePar;

    @Column(name = "depose_le", nullable = false, updatable = false)
    private Instant deposeLe;

    /** Métadonnées du dépôt (objet-paramètre). */
    public record Donnees(UUID sessionId, UUID encaissementId, Nature nature, String commentaire, String nomFichier, String typeMime,
            long tailleOctets, String empreinteSha256, String cheminStockage, UUID deposePar, Instant deposeLe) {
    }

    public JustificatifCaisse(Donnees d) {
        this.sessionId = d.sessionId();
        this.encaissementId = d.encaissementId();
        this.nature = d.nature();
        this.commentaire = d.commentaire();
        this.nomFichier = d.nomFichier();
        this.typeMime = d.typeMime();
        this.tailleOctets = d.tailleOctets();
        this.empreinteSha256 = d.empreinteSha256();
        this.cheminStockage = d.cheminStockage();
        this.deposePar = d.deposePar();
        this.deposeLe = d.deposeLe();
    }

    /** Nature de la pièce. */
    public enum Nature { TICKET_CARTE, RECU_MOBILE, BORDEREAU_REMISE, FICHE_COMPTAGE, AUTRE }
}
