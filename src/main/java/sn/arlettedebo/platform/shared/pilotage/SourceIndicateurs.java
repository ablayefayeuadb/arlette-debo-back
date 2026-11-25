package sn.arlettedebo.platform.shared.pilotage;

import java.util.List;
import java.util.Set;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Contribution d'un module au tableau de bord et au reporting.
 * <p>Inversion de dépendance : le module {@code pilotage} agrège toutes les implémentations sans
 * connaître les modules métier. Il n'appelle une source que si l'utilisateur a au moins C sur
 * {@link #module()} ; la source applique elle-même les restrictions fines (limité, produits…).
 */
public interface SourceIndicateurs {

    ModuleCode module();

    /** Ordre d'affichage des sections. */
    int ordre();

    String titre();

    /**
     * @param perimetres périmètres déjà restreints à ceux de l'utilisateur
     */
    List<Indicateur> calculer(Periode periode, Set<String> perimetres);

    /**
     * La source fournit-elle des indicateurs du comparatif des périmètres (SKY / KIDS / Boutique) ?
     * Le comparatif recalcule chaque source contributrice par périmètre : les autres ne sont pas appelées.
     */
    default boolean contribueAuComparatif() {
        return false;
    }

    /**
     * Inclure dans la vue dashboard « administratif » (Administration) ?
     * Ex. le commercial (fiches clientes) reste hors de cette vue.
     */
    default boolean pourVueAdministratif() {
        return true;
    }
}
