package sn.arlettedebo.platform.shared.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        boolean enabled,
        String username,
        String apiKey,
        String senderId,
        String baseUrl) {
}
