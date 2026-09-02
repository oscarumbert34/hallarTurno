package com.turnero.email;

import com.turnero.user.User;
import com.turnero.user.UserRole;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class UserWelcomeEmailService {

    private static final Logger log = LoggerFactory.getLogger(UserWelcomeEmailService.class);

    private final BrevoProperties properties;
    private final RestClient.Builder restClientBuilder;

    public UserWelcomeEmailService(BrevoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public void sendWelcomeEmail(User user) {
        if (!properties.isConfigured()) {
            log.debug("brevo welcome email skipped because configuration is incomplete");
            return;
        }

        try {
            restClientBuilder
                    .baseUrl(properties.getBaseUrl())
                    .build()
                    .post()
                    .uri("/v3/smtp/email")
                    .header("api-key", properties.getApiKey())
                    .body(toRequest(user))
                    .retrieve()
                    .toBodilessEntity();
            log.info("welcome email sent userId={} email={}", user.getId(), user.getEmail());
        } catch (RuntimeException exception) {
            log.warn("welcome email could not be sent userId={} email={}", user.getId(), user.getEmail(), exception);
        }
    }

    private BrevoEmailRequest toRequest(User user) {
        return new BrevoEmailRequest(
                new BrevoEmailAddress(properties.getFromEmail(), properties.getFromName()),
                List.of(new BrevoEmailAddress(user.getEmail(), null)),
                "Bienvenido/a a HallarTurno",
                plainTextContent(user),
                htmlContent(user)
        );
    }

    private String plainTextContent(User user) {
        String accountType = accountType(user);
        return """
                Hola,

                Tu cuenta de HallarTurno fue creada correctamente como %s.

                Ya podes iniciar sesion y empezar a usar la plataforma.

                Equipo de HallarTurno
                """.formatted(accountType);
    }

    private String htmlContent(User user) {
        String accountType = accountType(user);
        return """
                <p>Hola,</p>
                <p>Tu cuenta de <strong>HallarTurno</strong> fue creada correctamente como %s.</p>
                <p>Ya podes iniciar sesion y empezar a usar la plataforma.</p>
                <p>Equipo de HallarTurno</p>
                """.formatted(accountType);
    }

    private String accountType(User user) {
        return user.getRoles().contains(UserRole.BUSINESS) ? "negocio" : "cliente";
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
