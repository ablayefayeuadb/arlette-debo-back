package sn.arlettedebo.platform.shared.sms;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import sn.arlettedebo.platform.shared.telephone.Telephones;

@Service
public class AfricasTalkingSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(AfricasTalkingSmsService.class);

    private final SmsProperties props;
    private final RestClient http;

    public AfricasTalkingSmsService(SmsProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.baseUrl() == null || props.baseUrl().isBlank()
                ? "https://api.sandbox.africastalking.com"
                : props.baseUrl()).build();
    }

    @Override
    public void envoyer(String telephoneNationalOuMasque, String message) {
        if (!props.enabled()) {
            log.debug("SMS désactivé — non envoyé vers {}", telephoneNationalOuMasque);
            return;
        }
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            log.warn("SMS activé mais AT_API_KEY manquant — message non envoyé");
            return;
        }
        String e164;
        try {
            e164 = Telephones.e164(telephoneNationalOuMasque);
        } catch (RuntimeException e) {
            log.warn("SMS ignoré : téléphone invalide ({})", telephoneNationalOuMasque);
            return;
        }
        if (e164 == null || message == null || message.isBlank()) {
            return;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", props.username() == null || props.username().isBlank() ? "sandbox" : props.username());
            form.add("to", e164);
            form.add("message", message);
            if (props.senderId() != null && !props.senderId().isBlank()) {
                form.add("from", props.senderId());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.post()
                    .uri("/version1/messaging")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("apiKey", props.apiKey())
                    .header("Accept", "application/json")
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            log.info("SMS envoyé à {} — réponse AT: {}", e164, body);
        } catch (Exception e) {
            log.error("Échec envoi SMS vers {} : {}", e164, e.toString());
        }
    }
}
