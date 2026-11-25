package sn.arlettedebo.platform.finance.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.domain.BaseEntity;

/** Pièce justificative (devis, facture, reçu) — empreinte SHA-256 pour l'intégrité. */
@Getter
@Entity
@Table(name = "td_justificatif")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Justificatif extends BaseEntity {

    @Column(name = "depense_id", nullable = false, updatable = false)
    private UUID depenseId;

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

    /** Métadonnées du fichier déposé (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(UUID depenseId, String nomFichier, String typeMime, long tailleOctets,
            String empreinteSha256, String cheminStockage, UUID deposePar, Instant deposeLe) {
    }

    public Justificatif(Donnees d) {
        this.depenseId = d.depenseId();
        this.nomFichier = d.nomFichier();
        this.typeMime = d.typeMime();
        this.tailleOctets = d.tailleOctets();
        this.empreinteSha256 = d.empreinteSha256();
        this.cheminStockage = d.cheminStockage();
        this.deposePar = d.deposePar();
        this.deposeLe = d.deposeLe();
    }
}
