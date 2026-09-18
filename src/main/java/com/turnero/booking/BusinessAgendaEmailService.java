package com.turnero.booking;

import com.turnero.business.Business;
import com.turnero.email.BrevoTransactionalEmailClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class BusinessAgendaEmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final BrevoTransactionalEmailClient emailClient;

    public BusinessAgendaEmailService(BrevoTransactionalEmailClient emailClient) {
        this.emailClient = emailClient;
    }

    boolean sendAgenda(LocalDate agendaDate, List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return false;
        }
        Business business = bookings.getFirst().getBusiness();
        String recipientEmail = business.getContactEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = business.getOwner().getEmail();
        }
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return false;
        }
        return emailClient.sendEmail(
                recipientEmail,
                business.getName(),
                "Agenda de mañana - %s (%d turnos)".formatted(
                        agendaDate.format(DATE_FORMATTER), bookings.size()),
                plainTextContent(agendaDate, business, bookings),
                htmlContent(agendaDate, business, bookings)
        );
    }

    private String plainTextContent(LocalDate date, Business business, List<Booking> bookings) {
        StringBuilder rows = new StringBuilder();
        for (Booking booking : bookings) {
            LocalDateTime startsAt = localStartsAt(booking);
            rows.append("- %s | %s | %s | %s | %s\n".formatted(
                    startsAt.format(TIME_FORMATTER), booking.getCustomerNameSnapshot(),
                    booking.getServiceNameSnapshot(), booking.getBranch().getName(), booking.getResourceNameSnapshot()));
        }
        return """
                Agenda de mañana de %s

                Fecha: %s
                Total de turnos: %d

                %s
                Equipo de HallarTurno
                """.formatted(business.getName(), date.format(DATE_FORMATTER), bookings.size(), rows);
    }

    private String htmlContent(LocalDate date, Business business, List<Booking> bookings) {
        StringBuilder rows = new StringBuilder();
        for (Booking booking : bookings) {
            LocalDateTime startsAt = localStartsAt(booking);
            rows.append("""
                    <tr>
                      <td style="padding:10px;border-bottom:1px solid #e5e7eb">%s</td>
                      <td style="padding:10px;border-bottom:1px solid #e5e7eb">%s</td>
                      <td style="padding:10px;border-bottom:1px solid #e5e7eb">%s</td>
                      <td style="padding:10px;border-bottom:1px solid #e5e7eb">%s</td>
                      <td style="padding:10px;border-bottom:1px solid #e5e7eb">%s</td>
                    </tr>
                    """.formatted(startsAt.format(TIME_FORMATTER), escape(booking.getCustomerNameSnapshot()),
                    escape(booking.getServiceNameSnapshot()), escape(booking.getBranch().getName()),
                    escape(booking.getResourceNameSnapshot())));
        }
        return """
                <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Agenda de mañana</title></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#222">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px"><tr><td align="center">
                <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:760px;background:#fff;border-radius:14px;overflow:hidden">
                <tr><td style="padding:28px;background:#2563eb;color:#fff"><h1 style="margin:0;font-size:24px">Agenda de mañana</h1><div style="margin-top:8px">%s · %s · %d turnos</div></td></tr>
                <tr><td style="padding:28px;overflow-x:auto"><table width="100%%" cellpadding="0" cellspacing="0"><thead><tr style="text-align:left;background:#f3f4f6"><th style="padding:10px">Hora</th><th style="padding:10px">Cliente</th><th style="padding:10px">Servicio</th><th style="padding:10px">Sucursal</th><th style="padding:10px">Profesional/recurso</th></tr></thead><tbody>%s</tbody></table></td></tr>
                </table></td></tr></table></body></html>
                """.formatted(escape(business.getName()), date.format(DATE_FORMATTER), bookings.size(), rows);
    }

    private LocalDateTime localStartsAt(Booking booking) {
        return LocalDateTime.ofInstant(booking.getStartsAt(), ZoneId.of(booking.getBranch().getZoneId()));
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

}
