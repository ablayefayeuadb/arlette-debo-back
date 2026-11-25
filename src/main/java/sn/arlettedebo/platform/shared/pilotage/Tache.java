package sn.arlettedebo.platform.shared.pilotage;

import java.time.Instant;
import java.util.UUID;
import sn.arlettedebo.platform.shared.security.ActionCode;
import sn.arlettedebo.platform.shared.security.ModuleCode;

/**
 * Élément en attente d'une action de l'utilisateur courant (boîte « À traiter »).
 *
 * @param type       nature de l'action attendue (VALIDER_DEPENSE, AUTORISER_REMISE…)
 * @param niveau     droit mobilisé (V = validation / arbitrage, G = gestion, C = contrôle)
 * @param ressource  chemin API de la ressource concernée
 * @param montant    montant en jeu, s'il y en a un (tri par enjeu)
 */
public record Tache(UUID id, ModuleCode module, String type, ActionCode niveau, String titre, String detail, String ressource,
        Long montant, String perimetre, Instant depuis) {
}
