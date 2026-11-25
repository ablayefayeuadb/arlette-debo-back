package sn.arlettedebo.platform.shared.sms;

/** Envoi de SMS métier (Africa's Talking). Échec d'envoi journalisé, sans faire échouer le flux métier. */
public interface SmsService {

    void envoyer(String telephoneNationalOuMasque, String message);
}
