package sn.arlettedebo.platform.planning;

import java.util.UUID;

/** API publique du planning pour la qualité : vérification des prestations réalisées. */
public interface Prestations {

    /**
     * Marque une prestation terminée comme vérifiée (contrôle qualité). Appelée dans la transaction du contrôle.
     *
     * @return la prestation contrôlée (salon, code prestation, coiffeuse)
     */
    PrestationRealisee verifier(UUID rendezVousId, boolean conforme);

    record PrestationRealisee(UUID id, String perimetre, String prestation, UUID coiffeuseId) {
    }
}
