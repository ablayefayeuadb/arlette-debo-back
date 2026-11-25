package sn.arlettedebo.platform.shared.mail;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;
    /** Si renseigné (ex. {@code yopmail.com}), tout destinataire est réécrit en {@code local@domaine}. */
    private final String rewriteDomain;

    SmtpMailService(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.rewrite-domain:}") String rewriteDomain) {
        this.mailSender = mailSender;
        this.from = username == null ? "" : username.trim();
        this.enabled = !this.from.isBlank() && password != null && !password.isBlank();
        this.rewriteDomain = rewriteDomain == null ? "" : rewriteDomain.trim().toLowerCase();
    }

    @Override
    public void accesAbonnee(String destinataire, String nom, String motDePasseProvisoire, String loginUrl) {
        String to = normaliser(destinataire);
        if (to.isBlank()) {
            return;
        }
        String prenom = nom == null || nom.isBlank() ? "Madame" : nom.trim();
        String sujet = "Arlette Debo — votre accès portail abonnée";
        String corps = """
                Bonjour %s,

                Votre espace abonnée Arlette Debo est prêt.

                Connectez-vous ici : %s

                Identifiant : %s
                Mot de passe provisoire : %s

                Vous devrez modifier ce mot de passe à la première connexion.

                L’équipe Arlette Debo
                """.formatted(prenom, loginUrl, to, motDePasseProvisoire);
        envoyer(to, sujet, corps, true);
    }

    @Override
    public void nouvelleReservationEnLigne(String destinataire, String nomManager, String salon, LocalDate jour,
            LocalTime heure, String cliente, String telephone, String prestation, String reference, String lienAgenda) {
        String to = normaliser(destinataire);
        if (to.isBlank()) {
            return;
        }
        String prenom = nomManager == null || nomManager.isBlank() ? "Manager" : nomManager.trim();
        String sujet = "Arlette Debo — réservation en ligne à confirmer (" + (reference == null ? "RDV" : reference) + ")";
        String corps = """
                Bonjour %s,

                Une nouvelle réservation en ligne attend votre confirmation.

                Salon : %s
                Date : %s à %s
                Cliente : %s
                Téléphone : %s
                Prestation : %s
                Référence : %s

                Ouvrir le planning : %s

                L’équipe Arlette Debo
                """.formatted(
                prenom,
                salon == null ? "—" : salon,
                jour == null ? "—" : JOUR.format(jour),
                heure == null ? "—" : HEURE.format(heure),
                cliente == null || cliente.isBlank() ? "—" : cliente.trim(),
                telephone == null || telephone.isBlank() ? "—" : telephone.trim(),
                prestation == null || prestation.isBlank() ? "—" : prestation.trim(),
                reference == null || reference.isBlank() ? "—" : reference.trim(),
                lienAgenda == null || lienAgenda.isBlank() ? "—" : lienAgenda.trim());
        envoyer(to, sujet, corps, false);
    }

    private void envoyer(String toMetier, String sujet, String corps, boolean echecBloquant) {
        String to = reecrire(toMetier);
        if (!enabled) {
            log.warn("SMTP non configuré — e-mail non envoyé à {} (cible {})", toMetier, to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(sujet);
            helper.setText(corps, false);
            mailSender.send(message);
            if (to.equals(toMetier)) {
                log.info("E-mail envoyé à {}", to);
            } else {
                log.info("E-mail envoyé à {} (réécrit depuis {})", to, toMetier);
            }
        } catch (Exception e) {
            log.error("Échec envoi e-mail à {} (cible {}) : {}", toMetier, to, e.getMessage());
            if (echecBloquant) {
                throw sn.arlettedebo.platform.shared.error.DomainException.invalide(
                        "Impossible d’envoyer l’e-mail d’accès. Vérifiez l’adresse ou réessayez.");
            }
        }
    }

    private static String normaliser(String destinataire) {
        return destinataire == null ? "" : destinataire.trim().toLowerCase();
    }

    private String reecrire(String to) {
        if (rewriteDomain.isBlank() || to.isBlank()) {
            return to;
        }
        int at = to.lastIndexOf('@');
        String local = at > 0 ? to.substring(0, at) : to;
        if (local.isBlank()) {
            return to;
        }
        return local + "@" + rewriteDomain;
    }
}
