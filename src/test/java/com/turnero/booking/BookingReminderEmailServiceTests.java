package com.turnero.booking;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.turnero.branch.Branch;
import com.turnero.business.Business;
import com.turnero.email.BrevoProperties;
import com.turnero.email.BrevoTransactionalEmailClient;
import com.turnero.employee.BookableResource;
import com.turnero.service.ServiceOffering;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BookingReminderEmailServiceTests {

    @Test
    void sendReminderUsesBrevoTransactionalEmailApiWhenBookingHasEmail() {
        BrevoProperties properties = configuredProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BookingReminderEmailService service = new BookingReminderEmailService(
                new BrevoTransactionalEmailClient(properties, builder)
        );

        server.expect(once(), requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "brevo-secret"))
                .andExpect(content().string(containsString("\"email\":\"no-reply@hallarturno.com.ar\"")))
                .andExpect(content().string(containsString("\"email\":\"ana@example.com\"")))
                .andExpect(content().string(containsString("\"subject\":\"Recordatorio de tu turno en HallarTurno\"")))
                .andExpect(content().string(containsString("Te recordamos tu turno para 08/09/2026 a las 10:00.")))
                .andRespond(withSuccess());

        org.assertj.core.api.Assertions.assertThat(service.sendReminder(booking("ana@example.com"))).isTrue();

        server.verify();
    }

    @Test
    void sendReminderSkipsBrevoWhenBookingHasNoEmail() {
        BrevoProperties properties = configuredProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BookingReminderEmailService service = new BookingReminderEmailService(
                new BrevoTransactionalEmailClient(properties, builder)
        );

        org.assertj.core.api.Assertions.assertThat(service.sendReminder(booking(null))).isFalse();

        server.verify();
    }

    private Booking booking(String customerEmail) {
        Branch branch = org.mockito.Mockito.mock(Branch.class);
        Business business = org.mockito.Mockito.mock(Business.class);
        ServiceOffering serviceOffering = org.mockito.Mockito.mock(ServiceOffering.class);
        BookableResource resource = org.mockito.Mockito.mock(BookableResource.class);
        org.mockito.Mockito.when(branch.getZoneId()).thenReturn("America/Argentina/Buenos_Aires");
        org.mockito.Mockito.when(branch.getName()).thenReturn("Sucursal Centro");
        org.mockito.Mockito.when(business.getName()).thenReturn("Barberia Sur");
        return Booking.create(
                branch,
                business,
                null,
                serviceOffering,
                resource,
                Instant.parse("2026-09-08T13:00:00Z"),
                Instant.parse("2026-09-08T13:30:00Z"),
                "Corte",
                "Juan",
                "Ana",
                "+54 11 5555-1234",
                customerEmail,
                null,
                30,
                BigDecimal.valueOf(1500),
                "ARS",
                BookingStatus.CONFIRMED
        );
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
