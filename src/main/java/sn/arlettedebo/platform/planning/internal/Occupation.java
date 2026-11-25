package sn.arlettedebo.platform.planning.internal;

import java.util.List;
import sn.arlettedebo.platform.planning.internal.domain.Creneau;
import sn.arlettedebo.platform.planning.internal.domain.RendezVousRepository.JourHeure;

/**
 * Places prises par créneau, calculées à partir d'une seule requête de lot
 * ({@code RendezVousRepository.prisesEntre}) au lieu d'un comptage SQL par créneau.
 */
final class Occupation {

    private Occupation() {
    }

    /** Rendez-vous commençant dans [début, fin[ du créneau, le même jour. */
    static long dans(List<JourHeure> prises, Creneau c) {
        return prises.stream()
                .filter(p -> p.jour().equals(c.getJour()))
                .filter(p -> !p.heure().isBefore(c.getHeureDebut()) && p.heure().isBefore(c.getHeureFin()))
                .count();
    }
}
