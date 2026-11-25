package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Photo d'évolution capillaire rattachée à un dossier abonnée (optionnellement à une séance). */
@Getter
@Entity
@Table(name = "td_photo_evolution_abonnee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoEvolutionAbonnee extends AuditableEntity {

    @Column(name = "dossier_id", nullable = false, updatable = false)
    private UUID dossierId;

    @Column(name = "suivi_id")
    private UUID suiviId;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_photo", nullable = false, length = 20)
    private Type typePhoto;

    @Column(name = "legende")
    private String legende;

    @Column(name = "nom_fichier", nullable = false, updatable = false)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false, updatable = false)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false, updatable = false)
    private long tailleOctets;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "empreinte_sha256", nullable = false, updatable = false, length = 64)
    private String empreinteSha256;

    @Column(name = "chemin_stockage", nullable = false, updatable = false)
    private String cheminStockage;

    @Column(name = "depose_par", nullable = false, updatable = false)
    private UUID deposePar;

    @Column(name = "depose_le", nullable = false, updatable = false)
    private Instant deposeLe;

    public record Donnees(UUID dossierId, UUID suiviId, LocalDate jour, Type typePhoto, String legende, String nomFichier,
            String typeMime, long tailleOctets, String empreinteSha256, String cheminStockage, UUID deposePar, Instant deposeLe) {
    }

    public PhotoEvolutionAbonnee(Donnees d) {
        this.dossierId = d.dossierId();
        this.suiviId = d.suiviId();
        this.jour = d.jour();
        this.typePhoto = d.typePhoto();
        this.legende = d.legende();
        this.nomFichier = d.nomFichier();
        this.typeMime = d.typeMime();
        this.tailleOctets = d.tailleOctets();
        this.empreinteSha256 = d.empreinteSha256();
        this.cheminStockage = d.cheminStockage();
        this.deposePar = d.deposePar();
        this.deposeLe = d.deposeLe();
    }

    public enum Type {
        /** Point de la timeline (séance ou date libre). */
        EVOLUTION,
        /** Repère « before » (bilan / départ). */
        AVANT,
        /** Repère « after » (bilan / résultat). */
        APRES
    }
}
