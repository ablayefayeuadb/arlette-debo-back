package sn.arlettedebo.platform.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Entrée du journal d'audit — append-only (aucune mise à jour, aucune suppression applicative). */
@Getter
@Entity
@Immutable
@Table(name = "td_journal_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EntreeJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "horodatage", nullable = false, updatable = false)
    private Instant horodatage;

    @Column(name = "utilisateur_id", updatable = false)
    private UUID utilisateurId;

    @Column(name = "utilisateur_nom", updatable = false)
    private String utilisateurNom;

    @Column(name = "profil_code", updatable = false)
    private String profilCode;

    @Column(name = "module_code", nullable = false, updatable = false)
    private String moduleCode;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "adresse_ip", updatable = false)
    private String adresseIp;

    /** Entrée du journal d'audit (objet-paramètre : champs nommés plutôt qu'une longue liste positionnelle). */
    public record Donnees(Instant horodatage, UUID utilisateurId, String utilisateurNom, String profilCode,
            String moduleCode, String action, String detail, String adresseIp) {
    }

    EntreeJournal(Donnees d) {
        this.horodatage = d.horodatage();
        this.utilisateurId = d.utilisateurId();
        this.utilisateurNom = d.utilisateurNom();
        this.profilCode = d.profilCode();
        this.moduleCode = d.moduleCode();
        this.action = tronquer(d.action(), 120);
        this.detail = tronquer(d.detail(), 1000);
        this.adresseIp = d.adresseIp();
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
