package sn.arlettedebo.platform.iam.internal.domain;

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

/**
 * Jeton de rafraîchissement opaque. Seule son empreinte SHA-256 est stockée.
 * Les jetons d'une même session partagent une {@code famille} : la réutilisation d'un jeton
 * déjà consommé révoque toute la famille (détection de vol).
 */
@Getter
@Entity
@Table(name = "td_jeton_rafraichissement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JetonRafraichissement extends BaseEntity {

    @Column(name = "utilisateur_id", nullable = false, updatable = false)
    private UUID utilisateurId;

    @Column(name = "empreinte", nullable = false, updatable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String empreinte;

    @Column(name = "famille", nullable = false, updatable = false)
    private UUID famille;

    @Column(name = "expire_le", nullable = false, updatable = false)
    private Instant expireLe;

    @Column(name = "revoque_le")
    private Instant revoqueLe;

    @Column(name = "remplace_par")
    private UUID remplacePar;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "adresse_ip", updatable = false)
    private String adresseIp;

    @Column(name = "agent", updatable = false)
    private String agent;

    public JetonRafraichissement(UUID utilisateurId, String empreinte, UUID famille, Instant creeLe, Instant expireLe,
            String adresseIp, String agent) {
        this.utilisateurId = utilisateurId;
        this.empreinte = empreinte;
        this.famille = famille;
        this.creeLe = creeLe;
        this.expireLe = expireLe;
        this.adresseIp = adresseIp;
        this.agent = agent == null ? null : agent.substring(0, Math.min(agent.length(), 255));
    }

    public boolean estRevoque() {
        return revoqueLe != null;
    }

    public boolean estExpire(Instant maintenant) {
        return !expireLe.isAfter(maintenant);
    }

    public void remplacer(UUID successeur, Instant maintenant) {
        this.revoqueLe = maintenant;
        this.remplacePar = successeur;
    }
}
