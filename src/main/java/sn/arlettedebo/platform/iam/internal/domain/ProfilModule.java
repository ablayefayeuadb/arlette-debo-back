package sn.arlettedebo.platform.iam.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Cellule de la matrice profil × module : valeur brute ({@code "S/G*"}), actions, restriction,
 * marqueur « * » (sous habilitation) et état accordé/suspendu piloté par la DG.
 */
@Getter
@Entity
@Table(name = "tr_profil_module")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfilModule {

    @EmbeddedId
    private Cle id;

    @Column(name = "valeur_brute", nullable = false)
    private String valeurBrute;

    @Column(name = "restriction")
    private String restriction;

    @Column(name = "sous_habilitation", nullable = false)
    private boolean sousHabilitation;

    @Column(name = "accordee", nullable = false)
    private boolean accordee;

    @Column(name = "modifie_par")
    private UUID modifiePar;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_profil_module_action", joinColumns = {
            @JoinColumn(name = "profil_code", referencedColumnName = "profil_code"),
            @JoinColumn(name = "module_code", referencedColumnName = "module_code")})
    @Column(name = "action_code", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private Set<String> actions = new HashSet<>();

    /** Actions effectives : aucune si l'accès « * » est suspendu. */
    public Set<String> actionsEffectives() {
        return sousHabilitation && !accordee ? Set.of() : Set.copyOf(actions);
    }

    public void definirHabilitation(boolean accorder, UUID par, Instant le) {
        if (!sousHabilitation) {
            throw DomainException.etatInvalide("Ce droit n'est pas soumis à habilitation (pas de « * » dans la matrice).");
        }
        this.accordee = accorder;
        this.modifiePar = par;
        this.modifieLe = le;
    }

    @Getter
    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Cle implements Serializable {

        @Column(name = "profil_code", nullable = false)
        private String profilCode;

        @Column(name = "module_code", nullable = false)
        private String moduleCode;

        public Cle(String profilCode, String moduleCode) {
            this.profilCode = profilCode;
            this.moduleCode = moduleCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cle c && c.profilCode.equals(profilCode) && c.moduleCode.equals(moduleCode);
        }

        @Override
        public int hashCode() {
            return 31 * profilCode.hashCode() + moduleCode.hashCode();
        }
    }
}
