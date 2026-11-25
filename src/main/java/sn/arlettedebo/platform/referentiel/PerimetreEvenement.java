package sn.arlettedebo.platform.referentiel;

import java.util.UUID;

/**
 * Événements du référentiel des salons, publiés dans la transaction qui les produit.
 * Le module IAM y réagit : rattachement des profils transverses, vendeur désigné pour une boutique, droits.
 */
public sealed interface PerimetreEvenement {

    String code();

    /**
     * Nouveau salon ({@code SALON}) ou boutique autonome ({@code BOUTIQUE}).
     * {@code responsableId} : vendeur boutique désigné (obligatoire pour une boutique).
     */
    record Cree(String code, String type, UUID responsableId) implements PerimetreEvenement {
    }

    /** Salon renommé, désactivé ou réactivé. */
    record Modifie(String code, boolean actif) implements PerimetreEvenement {
    }
}
