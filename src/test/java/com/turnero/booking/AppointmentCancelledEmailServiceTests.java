package com.turnero.booking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.email.BrevoTransactionalEmailClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppointmentCancelledEmailServiceTests {

    private final BrevoTransactionalEmailClient emailClient = Mockito.mock(BrevoTransactionalEmailClient.class);
    private final AppointmentCancelledEmailService service = new AppointmentCancelledEmailService(emailClient);

    @Test
    void sendsBusinessAnEmailWithTheCancelledAppointmentDetails() {
        AppointmentCancelledEvent event = event("turnos@barberia.test");
        when(emailClient.sendEmail(
                eq("turnos@barberia.test"),
                eq("Barbería Norte"),
                eq("Turno cancelado - 15/09 16:30"),
                contains("Juan Pérez canceló su turno"),
                contains("Profesional/recurso")
        )).thenReturn(true);

        service.onAppointmentCancelled(event);

        verify(emailClient).sendEmail(
                eq("turnos@barberia.test"),
                eq("Barbería Norte"),
                eq("Turno cancelado - 15/09 16:30"),
                contains("Servicio: Corte de cabello"),
                contains("Marcos")
        );
    }

    @Test
    void skipsEmailWhenBusinessAndOwnerHaveNoAddress() {
        service.onAppointmentCancelled(event(null));

        verify(emailClient, never()).sendEmail(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void anEmailClientFailureDoesNotEscapeFromTheListener() {
        AppointmentCancelledEvent event = event("turnos@barberia.test");
        when(emailClient.sendEmail(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()
        )).thenThrow(new IllegalStateException("Brevo unavailable"));

        assertThatCode(() -> service.onAppointmentCancelled(event)).doesNotThrowAnyException();
    }

    private AppointmentCancelledEvent event(String recipientEmail) {
        return new AppointmentCancelledEvent(
                UUID.randomUUID(),
                recipientEmail,
                "Barbería Norte",
                "Juan Pérez",
                "Corte de cabello",
                Instant.parse("2026-09-15T19:30:00Z"),
                "America/Argentina/Buenos_Aires",
                "Barbería Norte",
                "Marcos"
        );
    }
}
