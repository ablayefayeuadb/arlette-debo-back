/**
 * Module « Pilotage » : tableau de bord, reporting et boîte « À traiter ».
 * Agrège les {@code SourceIndicateurs} et {@code SourceDeTaches} publiées par les modules métier,
 * sans dépendre d'aucun d'eux (seulement du noyau partagé et de l'IAM).
 */
@ApplicationModule(displayName = "Pilotage & reporting")
package sn.arlettedebo.platform.pilotage;

import org.springframework.modulith.ApplicationModule;
