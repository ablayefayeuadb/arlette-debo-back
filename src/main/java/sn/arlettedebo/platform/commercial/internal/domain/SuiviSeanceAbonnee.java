package sn.arlettedebo.platform.commercial.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/**
 * Suivi après une séance : cases cochées du calendrier de soins (catalogue {@code tp_option_suivi_soin}),
 * semaine, coiffeuse, observation. Un suivi par jour et par dossier.
 */
@Getter
@Entity
@Table(name = "td_suivi_seance_abonnee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuiviSeanceAbonnee extends AuditableEntity {

    @Column(name = "dossier_id", nullable = false, updatable = false)
    private UUID dossierId;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    /** N° de semaine du calendrier (Week N). */
    @Column(name = "numero_semaine")
    private Short numeroSemaine;

    @Column(name = "coiffeuse_id")
    private UUID coiffeuseId;

    /** Libellés dénormalisés (lisibilité / rétrocompat) — dérivés des options cochées. */
    @Column(name = "prepoo")
    private String prepoo;

    @Column(name = "shampoo")
    private String shampoo;

    @Column(name = "masque")
    private String masque;

    @Column(name = "soin_personnalise")
    private String soinPersonnalise;

    @Column(name = "leave_in")
    private String leaveIn;

    @Column(name = "mode_sechage")
    private String modeSechage;

    @Column(name = "coiffure_protectrice")
    private String coiffureProtectrice;

    @Column(name = "observation")
    private String observation;

    @BatchSize(size = 32)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tr_suivi_seance_option", joinColumns = @JoinColumn(name = "suivi_id"))
    @Column(name = "option_code", nullable = false)
    private Set<String> optionCodes = new LinkedHashSet<>();

    /** Saisie d'un suivi (objet-paramètre). */
    public record Donnees(UUID dossierId, LocalDate jour, Short numeroSemaine, UUID coiffeuseId, List<String> optionCodes,
            String coiffureProtectrice, String observation, String prepoo, String shampoo, String masque, String soinPersonnalise,
            String leaveIn, String modeSechage) {
    }

    public SuiviSeanceAbonnee(Donnees d) {
        this.dossierId = d.dossierId();
        appliquer(d);
    }

    public void modifier(Donnees d) {
        appliquer(d);
    }

    private void appliquer(Donnees d) {
        this.jour = d.jour();
        this.numeroSemaine = d.numeroSemaine();
        this.coiffeuseId = d.coiffeuseId();
        this.observation = d.observation();
        this.coiffureProtectrice = d.coiffureProtectrice();
        this.prepoo = d.prepoo();
        this.shampoo = d.shampoo();
        this.masque = d.masque();
        this.soinPersonnalise = d.soinPersonnalise();
        this.leaveIn = d.leaveIn();
        this.modeSechage = d.modeSechage();
        this.optionCodes.clear();
        if (d.optionCodes() != null) {
            this.optionCodes.addAll(d.optionCodes());
        }
    }

    public List<String> optionsOrdonnees() {
        return new ArrayList<>(optionCodes);
    }
}
