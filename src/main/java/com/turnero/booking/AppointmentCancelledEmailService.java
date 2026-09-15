package com.turnero.booking;

import com.turnero.email.BrevoTransactionalEmailClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

@Service
public class AppointmentCancelledEmailService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCancelledEmailService.class);
    private static final DateTimeFormatter SUBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final BrevoTransactionalEmailClient emailClient;

    public AppointmentCancelledEmailService(BrevoTransactionalEmailClient emailClient) {
        this.emailClient = emailClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        if (event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            log.warn("appointment cancellation email skipped because business has no email bookingId={}", event.bookingId());
            return;
        }
        LocalDateTime startsAt = LocalDateTime.ofInstant(event.startsAt(), ZoneId.of(event.zoneId()));
        try {
            boolean sent = emailClient.sendEmail(
                    event.recipientEmail(),
                    event.recipientName(),
                    "Turno cancelado - %s %s".formatted(
                            startsAt.format(SUBJECT_DATE_FORMATTER),
                            startsAt.format(TIME_FORMATTER)
                    ),
                    plainTextContent(event, startsAt),
                    htmlContent(event, startsAt)
            );
            if (sent) {
                log.info("appointment cancellation email sent bookingId={} recipientEmail={}",
                        event.bookingId(), event.recipientEmail());
            }
        } catch (RuntimeException exception) {
            log.warn("appointment cancellation email could not be sent bookingId={} recipientEmail={}",
                    event.bookingId(), event.recipientEmail(), exception);
        }
    }

    private String plainTextContent(AppointmentCancelledEvent event, LocalDateTime startsAt) {
        return """
                Se canceló un turno

                %s canceló su turno.

                Servicio: %s
                Fecha: %s
                Hora: %s
                Sucursal: %s
                Profesional/recurso: %s

                El horario volvió a quedar disponible para nuevas reservas.
                """.formatted(
                event.customerName(),
                event.serviceName(),
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                event.branchName(),
                event.resourceName()
        );
    }

    private String htmlContent(AppointmentCancelledEvent event, LocalDateTime startsAt) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Turno cancelado</title></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#222">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px;background:#f4f6f8"><tr><td align="center">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:#fff;border-radius:14px;overflow:hidden">
                      <tr><td align="center" style="padding:28px 20px;background:#dc2626;color:#fff"><div style="font-size:30px;font-weight:700">HallarTurno</div><div style="margin-top:6px;font-size:14px">Turno cancelado</div></td></tr>
                      <tr><td style="padding:40px 36px">
                        <h1 style="margin:0 0 20px;font-size:24px;color:#111827">Se canceló un turno</h1>
                        <p style="margin:0 0 24px;font-size:16px;line-height:1.6;color:#4b5563"><strong>%s</strong> canceló su turno.</p>
                        <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:10px">
                          <tr><td style="padding:12px 18px;color:#6b7280">Servicio</td><td style="padding:12px 18px;font-weight:600">%s</td></tr>
                          <tr><td style="padding:12px 18px;color:#6b7280">Fecha</td><td style="padding:12px 18px;font-weight:600">%s</td></tr>
                          <tr><td style="padding:12px 18px;color:#6b7280">Hora</td><td style="padding:12px 18px;font-weight:600">%s</td></tr>
                          <tr><td style="padding:12px 18px;color:#6b7280">Sucursal</td><td style="padding:12px 18px;font-weight:600">%s</td></tr>
                          <tr><td style="padding:12px 18px;color:#6b7280">Profesional/recurso</td><td style="padding:12px 18px;font-weight:600">%s</td></tr>
                        </table>
                        <p style="margin:24px 0 0;font-size:16px;line-height:1.6;color:#4b5563">El horario volvió a quedar disponible para nuevas reservas.</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body>
                </html>
                """.formatted(
                escape(event.customerName()),
                escape(event.serviceName()),
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                escape(event.branchName()),
                escape(event.resourceName())
        );
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
