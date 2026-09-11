package com.turnero.booking;

import com.turnero.email.BrevoTransactionalEmailClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class BookingConfirmationEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationEmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final BrevoTransactionalEmailClient emailClient;

    public BookingConfirmationEmailService(final BrevoTransactionalEmailClient emailClient) {
        this.emailClient = emailClient;
    }

    void sendConfirmation(final Booking booking) {
        final String email = booking.getCustomerEmailSnapshot();
        if (email == null || email.isBlank()) {
            return;
        }
        if (this.emailClient.sendEmail(
                email,
                booking.getCustomerNameSnapshot(),
                "Confirmacion de tu turno en HallarTurno",
                this.plainTextContent(booking),
                this.htmlContent(booking)
        )) {
            log.info("booking confirmation email sent bookingId={} recipientEmail={}", booking.getId(), email);
        }
    }

    void sendReschedule(final Booking booking) {
        final String email = booking.getCustomerEmailSnapshot();
        if (email == null || email.isBlank()) {
            return;
        }
        if (this.emailClient.sendEmail(
                email,
                booking.getCustomerNameSnapshot(),
                "Reprogramacion de tu turno en HallarTurno",
                this.reschedulePlainTextContent(booking),
                this.rescheduleHtmlContent(booking)
        )) {
            log.info("booking reschedule email sent bookingId={} recipientEmail={}", booking.getId(), email);
        }
    }

    private String reschedulePlainTextContent(final Booking booking) {
        final LocalDateTime startsAt = this.localStartsAt(booking);
        return """
                Hola %s,

                Tu turno fue reprogramado para %s a las %s.

                Servicio: %s
                Negocio: %s
                Sucursal: %s

                Equipo de HallarTurno
                """.formatted(
                booking.getCustomerNameSnapshot(),
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                booking.getServiceNameSnapshot(),
                booking.getBusiness().getName(),
                booking.getBranch().getName()
        );
    }

    private String rescheduleHtmlContent(final Booking booking) {
        final LocalDateTime startsAt = this.localStartsAt(booking);
        final String customerName = escape(booking.getCustomerNameSnapshot());
        final String serviceName = escape(booking.getServiceNameSnapshot());
        final String businessName = escape(booking.getBusiness().getName());
        final String branchName = escape(booking.getBranch().getName());
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Reprogramacion de turno</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f6f8; font-family:Arial, Helvetica, sans-serif; color:#222222;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f8; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px; background-color:#ffffff; border-radius:14px; overflow:hidden;">
                          <tr>
                            <td align="center" style="background-color:#2563eb; padding:28px 20px; color:#ffffff;">
                              <div style="font-size:30px; font-weight:700;">HallarTurno</div>
                              <div style="font-size:14px; margin-top:6px; opacity:0.9;">Reprogramacion de turno</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 36px;">
                              <h1 style="margin:0 0 20px; font-size:24px; line-height:1.3; color:#111827;">Tu turno fue reprogramado</h1>
                              <p style="margin:0 0 18px; font-size:16px; line-height:1.6; color:#4b5563;">Hola %s,</p>
                              <p style="margin:0 0 24px; font-size:16px; line-height:1.6; color:#4b5563;">Estos son los nuevos datos de tu reserva.</p>
                              <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e5e7eb; border-radius:10px;">
                                <tr><td style="padding:14px 18px; color:#6b7280;">Fecha</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Hora</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Servicio</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Negocio</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Sucursal</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                              </table>
                              <p style="margin:28px 0 0; font-size:16px; line-height:1.6; color:#111827;"><strong>Equipo de HallarTurno</strong></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                customerName,
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                serviceName,
                businessName,
                branchName
        );
    }

    private String plainTextContent(final Booking booking) {
        final LocalDateTime startsAt = this.localStartsAt(booking);
        return """
                Hola %s,

                Tu turno fue confirmado para %s a las %s.

                Servicio: %s
                Negocio: %s
                Sucursal: %s

                Equipo de HallarTurno
                """.formatted(
                booking.getCustomerNameSnapshot(),
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                booking.getServiceNameSnapshot(),
                booking.getBusiness().getName(),
                booking.getBranch().getName()
        );
    }

    private String htmlContent(final Booking booking) {
        final LocalDateTime startsAt = this.localStartsAt(booking);
        final String customerName = escape(booking.getCustomerNameSnapshot());
        final String serviceName = escape(booking.getServiceNameSnapshot());
        final String businessName = escape(booking.getBusiness().getName());
        final String branchName = escape(booking.getBranch().getName());
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Confirmacion de turno</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f6f8; font-family:Arial, Helvetica, sans-serif; color:#222222;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f8; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px; background-color:#ffffff; border-radius:14px; overflow:hidden;">
                          <tr>
                            <td align="center" style="background-color:#2563eb; padding:28px 20px; color:#ffffff;">
                              <div style="font-size:30px; font-weight:700;">HallarTurno</div>
                              <div style="font-size:14px; margin-top:6px; opacity:0.9;">Confirmacion de turno</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 36px;">
                              <h1 style="margin:0 0 20px; font-size:24px; line-height:1.3; color:#111827;">Tu turno esta confirmado</h1>
                              <p style="margin:0 0 18px; font-size:16px; line-height:1.6; color:#4b5563;">Hola %s,</p>
                              <p style="margin:0 0 24px; font-size:16px; line-height:1.6; color:#4b5563;">Recibimos tu reserva correctamente.</p>
                              <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="border:1px solid #e5e7eb; border-radius:10px;">
                                <tr><td style="padding:14px 18px; color:#6b7280;">Fecha</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Hora</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Servicio</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Negocio</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                                <tr><td style="padding:14px 18px; color:#6b7280;">Sucursal</td><td style="padding:14px 18px; color:#111827; font-weight:600;">%s</td></tr>
                              </table>
                              <p style="margin:28px 0 0; font-size:16px; line-height:1.6; color:#111827;"><strong>Equipo de HallarTurno</strong></p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                customerName,
                startsAt.format(DATE_FORMATTER),
                startsAt.format(TIME_FORMATTER),
                serviceName,
                businessName,
                branchName
        );
    }

    private LocalDateTime localStartsAt(final Booking booking) {
        return LocalDateTime.ofInstant(booking.getStartsAt(), ZoneId.of(booking.getBranch().getZoneId()));
    }

    private String escape(final String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
