package com.turnero.email;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.turnero.user.User;
import com.turnero.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserWelcomeEmailServiceTests {

    @Test
    void sendWelcomeEmailUsesBrevoTransactionalEmailApi() {
        BrevoProperties properties = configuredProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserWelcomeEmailService service = new UserWelcomeEmailService(properties, builder);

        server.expect(once(), requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "brevo-secret"))
                .andExpect(content().string(containsString("\"email\":\"no-reply@hallarturno.com.ar\"")))
                .andExpect(content().string(containsString("\"name\":\"HallarTurno\"")))
                .andExpect(content().string(containsString("\"email\":\"owner@example.com\"")))
                .andExpect(content().string(containsString("\"subject\":\"Bienvenido/a a HallarTurno\"")))
                .andExpect(content().string(containsString("Tu cuenta de HallarTurno fue creada correctamente como negocio.")))
                .andRespond(withSuccess());

        service.sendWelcomeEmail(User.create("owner@example.com", "hash", UserRole.BUSINESS));

        server.verify();
    }

    @Test
    void sendWelcomeEmailSkipsBrevoWhenConfigurationIsIncomplete() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserWelcomeEmailService service = new UserWelcomeEmailService(new BrevoProperties(), builder);

        service.sendWelcomeEmail(User.create("customer@example.com", "hash", UserRole.CUSTOMER));

        server.verify();
    }

    private BrevoProperties configuredProperties() {
        BrevoProperties properties = new BrevoProperties();
        properties.setApiKey("brevo-secret");
        properties.setFromEmail("no-reply@hallarturno.com.ar");
        properties.setFromName("HallarTurno");
        properties.setBaseUrl("https://api.brevo.test");
        return properties;
    }
}
