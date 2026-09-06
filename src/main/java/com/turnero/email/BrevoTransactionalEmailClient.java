package com.turnero.email;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BrevoTransactionalEmailClient {

    private static final Logger log = LoggerFactory.getLogger(BrevoTransactionalEmailClient.class);

    private final BrevoProperties properties;
    private final RestClient.Builder restClientBuilder;

    public BrevoTransactionalEmailClient(final BrevoProperties properties, final RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public boolean sendEmail(
            final String recipientEmail,
            final String recipientName,
            final String subject,
            final String textContent,
            final String htmlContent
    ) {
        if (!this.properties.isConfigured()) {
            log.debug("brevo email skipped because configuration is incomplete");
            return false;
        }

        try {
            this.restClientBuilder
                    .baseUrl(this.properties.getBaseUrl())
                    .build()
                    .post()
                    .uri("/v3/smtp/email")
                    .header("api-key", this.properties.getApiKey())
                    .body(new BrevoEmailRequest(
                            new BrevoEmailAddress(this.properties.getFromEmail(), this.properties.getFromName()),
                            List.of(new BrevoEmailAddress(recipientEmail, recipientName)),
                            subject,
                            textContent,
                            htmlContent
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (final RuntimeException exception) {
            log.warn("brevo email could not be sent recipientEmail={}", recipientEmail, exception);
            return false;
        }
    }

    private record BrevoEmailRequest(
            BrevoEmailAddress sender,
            List<BrevoEmailAddress> to,
            String subject,
            String textContent,
            String htmlContent
    ) {
    }

    private record BrevoEmailAddress(String email, String name) {
    }
}
