package sn.arlettedebo.platform.shared.pilotage;

import java.util.List;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Contribution d'un module à la boîte « À traiter ». L'implémentation ne renvoie que ce que
 * l'utilisateur courant peut effectivement traiter (droits, périmètre, séparation des tâches).
 */
public interface SourceDeTaches {

    /** Module porté par ces tâches (filtre dashboard « limité » / administratif). */
    ModuleCode module();

    List<Tache> tachesEnAttente();
}
