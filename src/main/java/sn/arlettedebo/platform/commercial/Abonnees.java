package sn.arlettedebo.platform.commercial;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * API publique abonnées pour les autres modules (planning) : rattacher une séance
 * réalisée (RDV terminé) au dossier d'abonnée.
 */
public interface Abonnees {

    /**
     * Enregistre un suivi de séance pour le jour du rendez-vous, si un dossier
     * abonnée existe (par {@code clientId} ou téléphone + salon).
     * Idempotent : un suivi existe déjà pour ce jour → aucune action.
     *
     * @return {@code true} si un suivi a été créé
     */
    boolean enregistrerSeanceDuRendezVous(UUID clientId, String telephone, String perimetre, LocalDate jour,
            String observation);

    /** Sous-ensemble des {@code clientIds} qui ont déjà un dossier abonnée. */
    Set<UUID> clientIdsAvecDossier(Collection<UUID> clientIds);

    /** Dossier existant pour la fiche, ou à défaut pour téléphone + salon. */
    boolean aDossier(UUID clientId, String telephone, String perimetre);
}
