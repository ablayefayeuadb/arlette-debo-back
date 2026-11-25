package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Check-list hygiène d'une zone : chaque responsable valide sa zone, l'historique est conservé. */
@Getter
@Entity
@Table(name = "td_checklist_hygiene")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistHygiene extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    @Enumerated(EnumType.STRING)
    @Column(name = "moment", nullable = false)
    private Moment moment;

    @Column(name = "zone", nullable = false)
    private String zone;

    @Column(name = "responsable_id", nullable = false)
    private UUID responsableId;

    @Column(name = "validee_le")
    private Instant valideeLe;

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChecklistHygieneLigne> lignes = new ArrayList<>();

    public ChecklistHygiene(String perimetre, LocalDate jour, Moment moment, String zone, UUID responsable) {
        this.perimetreCode = perimetre;
        this.jour = jour;
        this.moment = moment;
        this.zone = zone;
        this.responsableId = responsable;
    }

    public void cocher(String itemCode, boolean conforme, String commentaire) {
        if (valideeLe != null) {
            throw DomainException.etatInvalide("Check-list déjà validée.");
        }
        lignes.removeIf(l -> l.getItemCode().equals(itemCode));
        lignes.add(new ChecklistHygieneLigne(this, itemCode, conforme, commentaire));
    }

    public void valider(Instant le) {
        if (lignes.isEmpty()) {
            throw DomainException.invalide("Check-list vide.");
        }
        valideeLe = le;
    }

    public boolean conforme() {
        return lignes.stream().allMatch(ChecklistHygieneLigne::isConforme);
    }

    public enum Moment { OUVERTURE, APRES_PRESTATION, FERMETURE }
}
