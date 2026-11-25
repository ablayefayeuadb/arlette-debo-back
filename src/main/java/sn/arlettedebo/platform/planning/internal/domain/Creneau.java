package sn.arlettedebo.platform.planning.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Créneau ouvert à la réservation en ligne. */
@Getter
@Entity
@Table(name = "td_creneau")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Creneau extends AuditableEntity {

    @Column(name = "perimetre_code", nullable = false)
    private String perimetreCode;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;

    @Column(name = "capacite", nullable = false)
    private short capacite;

    @Column(name = "ouvert", nullable = false)
    private boolean ouvert = true;

    public Creneau(String perimetre, LocalDate jour, LocalTime debut, LocalTime fin, int capacite) {
        if (!fin.isAfter(debut)) {
            throw DomainException.invalide("L'heure de fin doit suivre l'heure de début.");
        }
        this.perimetreCode = perimetre;
        this.jour = jour;
        this.heureDebut = debut;
        this.heureFin = fin;
        this.capacite = (short) capacite;
    }

    public void ouvrir(boolean ouvert) {
        this.ouvert = ouvert;
    }

    public void definirCapacite(int capacite) {
        if (capacite < 1 || capacite > 20) {
            throw DomainException.invalide("Capacité entre 1 et 20.");
        }
        this.capacite = (short) capacite;
    }
}
