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

/** Maintenance & locaux : déclaré → assigné → en cours → résolu → validé. */
@Getter
@Entity
@Table(name = "td_ticket_maintenance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketMaintenance extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "categorie", nullable = false)
    private String categorie;

    @Column(name = "local", nullable = false)
    private String local;

    @Column(name = "probleme", nullable = false)
    private String probleme;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgence", nullable = false)
    private Urgence urgence;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private Statut statut = Statut.DECLARE;

    @Column(name = "declare_par", nullable = false, updatable = false)
    private UUID declarePar;

    @Column(name = "assigne_a")
    private UUID assigneA;

    @Column(name = "resolu_le")
    private Instant resoluLe;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "valide_le")
    private Instant valideLe;

    public TicketMaintenance(String perimetre, String categorie, String local, String probleme, Urgence urgence, UUID declarePar) {
        this.perimetreCode = perimetre;
        this.categorie = categorie;
        this.local = local;
        this.probleme = probleme;
        this.urgence = urgence;
        this.declarePar = declarePar;
    }

    public void assigner(UUID a) {
        exiger(Statut.DECLARE, Statut.ASSIGNE);
        assigneA = a;
        statut = Statut.ASSIGNE;
    }

    public void demarrer() {
        exiger(Statut.ASSIGNE);
        statut = Statut.EN_COURS;
    }

    public void resoudre(Instant le) {
        exiger(Statut.EN_COURS);
        statut = Statut.RESOLU;
        resoluLe = le;
    }

    public void valider(UUID par, Instant le) {
        exiger(Statut.RESOLU);
        statut = Statut.VALIDE;
        validePar = par;
        valideLe = le;
    }

    private void exiger(Statut... attendus) {
        for (Statut s : attendus) {
            if (statut == s) {
                return;
            }
        }
        throw DomainException.etatInvalide("Étape impossible depuis le statut " + statut + ".");
    }

    public enum Urgence { BASSE, NORMALE, HAUTE }

    public enum Statut { DECLARE, ASSIGNE, EN_COURS, RESOLU, VALIDE }
}
