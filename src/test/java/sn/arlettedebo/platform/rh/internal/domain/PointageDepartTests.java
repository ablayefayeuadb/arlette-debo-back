package sn.arlettedebo.platform.rh.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Règles du départ : anticipé, non pointé, régularisation et justification (heures fixes, sans base). */
@DisplayName("Pointage : départ anticipé, départ non pointé, régularisation")
class PointageDepartTests {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 12);
    private static final UUID MANAGER = UUID.randomUUID();

    private static Instant a(int h, int m) {
        return JOUR.atTime(h, m).atZone(DAKAR).toInstant();
    }

    private static Pointage arrivee(int h, int m) {
        Pointage p = new Pointage(UUID.randomUUID(), JOUR, LocalTime.of(9, 30), LocalTime.of(19, 0));
        p.arriver(a(h, m), DAKAR, 5);
        return p;
    }

    @Test
    @DisplayName("Départ avant l'heure de fin au-delà de la tolérance : minutes d'avance enregistrées")
    void departAnticipe() {
        Pointage p = arrivee(9, 30);
        p.partir(a(18, 20), DAKAR, 5);
        assertThat(p.getDepartAnticipeMin()).isEqualTo(40);
        assertThat(p.statut()).isEqualTo(Pointage.Statut.PARTIE);

        Pointage dansTolerance = arrivee(9, 30);
        dansTolerance.partir(a(18, 56), DAKAR, 5);
        assertThat(dansTolerance.getDepartAnticipeMin()).isZero();

        Pointage apres = arrivee(9, 30);
        apres.partir(a(19, 30), DAKAR, 5);
        assertThat(apres.getDepartAnticipeMin()).isZero();
    }

    @Test
    @DisplayName("Départ pendant la pause : la pause est close à l'heure du départ")
    void departPendantPause() {
        Pointage p = arrivee(9, 30);
        p.commencerPause(a(13, 0));
        p.partir(a(14, 0), DAKAR, 5);
        assertThat(p.getPauseFin()).isEqualTo(a(14, 0));
        assertThat(p.getDepartAnticipeMin()).isEqualTo(300);
    }

    @Test
    @DisplayName("Arrivée sans départ : « départ non pointé », plus aucun scan possible, aucune heure inventée")
    void departNonPointe() {
        Pointage p = arrivee(9, 40);
        assertThat(p.signalerDepartNonPointe()).isTrue();
        assertThat(p.signalerDepartNonPointe()).isFalse();
        assertThat(p.statut()).isEqualTo(Pointage.Statut.DEPART_NON_POINTE);
        assertThat(p.actionsPossibles()).isEmpty();
        assertThat(p.getDepartLe()).isNull();
        Instant fin = a(19, 0);
        assertThatThrownBy(() -> p.partir(fin, DAKAR, 5)).isInstanceOf(DomainException.class);

        Pointage absente = new Pointage(UUID.randomUUID(), JOUR, LocalTime.of(9, 30), LocalTime.of(19, 0));
        assertThat(absente.signalerDepartNonPointe()).as("pas d'arrivée : rien à signaler").isFalse();
    }

    @Test
    @DisplayName("Régularisation : motif obligatoire, heure après l'arrivée et pas dans le futur ; départ anticipé recalculé")
    void regularisation() {
        Pointage p = arrivee(9, 30);
        p.signalerDepartNonPointe();
        Instant soir = a(23, 0);
        assertThatThrownBy(() -> p.regulariserDepart(LocalTime.of(18, 0), DAKAR, 5, MANAGER, " ", soir)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> p.regulariserDepart(LocalTime.of(9, 0), DAKAR, 5, MANAGER, "Oubli", soir)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> p.regulariserDepart(LocalTime.of(23, 30), DAKAR, 5, MANAGER, "Oubli", soir)).isInstanceOf(DomainException.class);

        p.regulariserDepart(LocalTime.of(18, 0), DAKAR, 5, MANAGER, "  Oubli du scan, confirmé par la caisse ", soir);
        assertThat(p.statut()).isEqualTo(Pointage.Statut.PARTIE);
        assertThat(p.getDepartLe()).isEqualTo(a(18, 0));
        assertThat(p.getDepartAnticipeMin()).isEqualTo(60);
        assertThat(p.getRegularisePar()).isEqualTo(MANAGER);
        assertThat(p.getMotifRegularisation()).isEqualTo("Oubli du scan, confirmé par la caisse");
        assertThat(p.isDepartNonPointe()).as("l'anomalie reste tracée").isTrue();
        assertThatThrownBy(() -> p.regulariserDepart(LocalTime.of(18, 30), DAKAR, 5, MANAGER, "Encore", soir))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Justification : seulement s'il y a un retard ou un départ anticipé, motif obligatoire")
    void justification() {
        Pointage aLHeure = arrivee(9, 30);
        aLHeure.partir(a(19, 0), DAKAR, 5);
        assertThatThrownBy(() -> aLHeure.justifier("Rien")).isInstanceOf(DomainException.class);

        Pointage tot = arrivee(9, 30);
        tot.partir(a(17, 0), DAKAR, 5);
        assertThatThrownBy(() -> tot.justifier(" ")).isInstanceOf(DomainException.class);
        tot.justifier("Rendez-vous médical autorisé");
        assertThat(tot.getJustification()).isEqualTo("Rendez-vous médical autorisé");
    }
}
