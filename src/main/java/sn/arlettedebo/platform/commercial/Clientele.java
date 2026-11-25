package sn.arlettedebo.platform.commercial;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * API publique du module commercial pour la caisse : rattachement d'un encaissement à une cliente
 * et application d'un geste commercial autorisé. Appelée dans la transaction de l'encaissement.
 */
public interface Clientele {

    /** Vérifie que la fiche existe et relève du périmètre ; renvoie le nom de la cliente. */
    String exigerCliente(UUID clientId, String perimetre);

    /** Fiche existante du périmètre pour ce numéro (réservation en ligne : rattachement sans création de fiche). */
    Optional<UUID> ficheParTelephone(String telephone, String perimetre);

    /**
     * Abonnée du salon pour ce numéro (statut ABONNEE). Sert à la réservation en ligne « Abonnée »
     * : le nom est repris de la fiche, sans saisie publique.
     */
    Optional<FicheAbonnee> abonneeParTelephone(String telephone, String perimetre);

    /** Fiche abonnée résolue par téléphone. */
    record FicheAbonnee(UUID clientId, String nom) {
    }

    /** Passage en caisse : dernière visite, coiffeuse habituelle, prospect → cliente. */
    void enregistrerVisite(UUID clientId, LocalDate jour, UUID coiffeuseId);

    /**
     * Applique en caisse un geste commercial autorisé (remise, gratuité…) du même périmètre.
     *
     * @return le montant du geste, en FCFA
     */
    long appliquerGeste(UUID demandeAutorisationId, String perimetre);
}
