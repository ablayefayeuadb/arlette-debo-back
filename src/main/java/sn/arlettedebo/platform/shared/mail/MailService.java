package sn.arlettedebo.platform.shared.mail;

import java.time.LocalDate;
import java.time.LocalTime;

/** Envoi d'e-mails transactionnels (SMTP). */
public interface MailService {

    /**
     * Accès portail abonnée : identifiant, mot de passe provisoire et lien de connexion
     * (changement de mot de passe obligatoire à la 1ʳᵉ connexion).
     */
    void accesAbonnee(String destinataire, String nom, String motDePasseProvisoire, String loginUrl);

    /**
     * Alerte manager : nouvelle réservation en ligne à confirmer / assigner.
     * Ne doit pas faire échouer la réservation (échec SMTP = log uniquement).
     */
    void nouvelleReservationEnLigne(String destinataire, String nomManager, String salon, LocalDate jour, LocalTime heure,
            String cliente, String telephone, String prestation, String reference, String lienAgenda);
}
