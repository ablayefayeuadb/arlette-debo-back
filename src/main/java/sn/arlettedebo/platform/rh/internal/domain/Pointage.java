package sn.arlettedebo.platform.rh.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;
import sn.arlettedebo.platform.shared.error.DomainException;

/**
 * Pointage journalier : arrivée → pause → retour → départ.
 * <ul>
 *   <li>retard calculé à l'arrivée, départ anticipé calculé au départ (heures prévues recopiées du dossier) ;</li>
 *   <li>arrivée sans départ à l'heure du contrôle : « départ non pointé », jamais d'heure inventée ;</li>
 *   <li>régularisation par la Manager ou l'Administration : heure réelle et motif obligatoire, tracés.</li>
 * </ul>
 */
@Getter
@Entity
@Table(name = "td_pointage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pointage extends AuditableEntity {

    @Column(name = "employe_id", nullable = false, updatable = false)
    private UUID employeId;

    @Column(name = "jour", nullable = false, updatable = false)
    private LocalDate jour;

    @Column(name = "heure_prevue", nullable = false)
    private LocalTime heurePrevue;

    @Column(name = "arrivee_le")
    private Instant arriveeLe;

    @Column(name = "pause_debut")
    private Instant pauseDebut;

    @Column(name = "pause_fin")
    private Instant pauseFin;

    @Column(name = "depart_le")
    private Instant departLe;

    @Column(name = "retard_min", nullable = false)
    private int retardMin;

    @Column(name = "justification")
    private String justification;

    @Column(name = "heure_fin_prevue")
    private LocalTime heureFinPrevue;

    @Column(name = "depart_anticipe_min", nullable = false)
    private int departAnticipeMin;

    @Column(name = "depart_non_pointe", nullable = false)
    private boolean departNonPointe;

    @Column(name = "regularise_par")
    private UUID regularisePar;

    @Column(name = "regularise_le")
    private Instant regulariseLe;

    @Column(name = "motif_regularisation")
    private String motifRegularisation;

    public Pointage(UUID employeId, LocalDate jour, LocalTime heurePrevue, LocalTime heureFinPrevue) {
        this.employeId = employeId;
        this.jour = jour;
        this.heurePrevue = heurePrevue;
        this.heureFinPrevue = heureFinPrevue;
    }

    public void arriver(Instant le, ZoneId zone, int toleranceMin) {
        if (arriveeLe != null) {
            throw DomainException.etatInvalide("Arrivée déjà pointée.");
        }
        arriveeLe = le;
        long ecart = Duration.between(jour.atTime(heurePrevue).atZone(zone).toInstant(), le).toMinutes();
        retardMin = ecart > toleranceMin ? (int) ecart : 0;
    }

    public void commencerPause(Instant le) {
        if (statut() != Statut.PRESENTE || pauseDebut != null) {
            throw DomainException.etatInvalide("Pause impossible : présence non pointée, pause déjà prise ou départ enregistré.");
        }
        pauseDebut = le;
    }

    public void finirPause(Instant le) {
        if (statut() != Statut.EN_PAUSE) {
            throw DomainException.etatInvalide("Aucune pause en cours.");
        }
        pauseFin = le;
    }

    /** Départ ; une pause en cours est close à l'heure du départ ; départ anticipé calculé au-delà de la tolérance. */
    public void partir(Instant le, ZoneId zone, int toleranceMin) {
        if (arriveeLe == null || departLe != null || departNonPointe) {
            throw DomainException.etatInvalide("Départ impossible : arrivée non pointée, départ déjà enregistré ou à régulariser.");
        }
        enregistrerDepart(le, zone, toleranceMin);
    }

    /** Contrôle du soir : arrivée sans départ ⇒ « départ non pointé » (aucune heure inventée). */
    public boolean signalerDepartNonPointe() {
        if (arriveeLe == null || departLe != null || departNonPointe) {
            return false;
        }
        departNonPointe = true;
        return true;
    }

    /**
     * Régularisation d'un départ oublié par la Manager ou l'Administration : heure réelle saisie, motif obligatoire.
     * L'heure doit être après l'arrivée et pas dans le futur.
     */
    public void regulariserDepart(LocalTime heure, ZoneId zone, int toleranceMin, UUID par, String motif, Instant maintenant) {
        if (arriveeLe == null || departLe != null) {
            throw DomainException.etatInvalide("Rien à régulariser : arrivée non pointée ou départ déjà enregistré.");
        }
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("Le motif de la régularisation est obligatoire.");
        }
        Instant depart = jour.atTime(heure).atZone(zone).toInstant();
        if (!depart.isAfter(arriveeLe) || depart.isAfter(maintenant)) {
            throw DomainException.invalide("L'heure de départ doit être après l'arrivée et ne peut pas être dans le futur.");
        }
        if (pauseDebut != null && depart.isBefore(pauseDebut)) {
            throw DomainException.invalide("L'heure de départ doit être après le début de la pause.");
        }
        enregistrerDepart(depart, zone, toleranceMin);
        regularisePar = par;
        regulariseLe = maintenant;
        motifRegularisation = motif.trim();
    }

    private void enregistrerDepart(Instant le, ZoneId zone, int toleranceMin) {
        if (pauseDebut != null && pauseFin == null) {
            pauseFin = le;
        }
        departLe = le;
        departAnticipeMin = 0;
        if (heureFinPrevue != null) {
            long avance = Duration.between(le, jour.atTime(heureFinPrevue).atZone(zone).toInstant()).toMinutes();
            departAnticipeMin = avance > toleranceMin ? (int) avance : 0;
        }
    }

    public Statut statut() {
        if (arriveeLe == null) {
            return Statut.ABSENTE;
        }
        if (departLe != null) {
            return Statut.PARTIE;
        }
        if (departNonPointe) {
            return Statut.DEPART_NON_POINTE;
        }
        return pauseDebut != null && pauseFin == null ? Statut.EN_PAUSE : Statut.PRESENTE;
    }

    /** Actions de pointage possibles dans l'état courant. */
    public java.util.List<Action> actionsPossibles() {
        return switch (statut()) {
            case ABSENTE -> java.util.List.of(Action.ARRIVEE);
            case PRESENTE -> pauseDebut == null ? java.util.List.of(Action.PAUSE_DEBUT, Action.DEPART) : java.util.List.of(Action.DEPART);
            case EN_PAUSE -> java.util.List.of(Action.PAUSE_FIN, Action.DEPART);
            case PARTIE, DEPART_NON_POINTE -> java.util.List.of();
        };
    }

    public enum Statut { ABSENTE, PRESENTE, EN_PAUSE, PARTIE, DEPART_NON_POINTE }

    public enum Action { ARRIVEE, PAUSE_DEBUT, PAUSE_FIN, DEPART }

    /** Justification d'un retard ou d'un départ anticipé (Manager, Administration). */
    public void justifier(String motif) {
        if (motif == null || motif.isBlank()) {
            throw DomainException.invalide("La justification est obligatoire.");
        }
        if (retardMin == 0 && departAnticipeMin == 0) {
            throw DomainException.etatInvalide("Ni retard ni départ anticipé à justifier sur cette journée.");
        }
        this.justification = motif.trim();
    }
}
