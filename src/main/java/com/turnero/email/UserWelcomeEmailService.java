package com.turnero.email;

import com.turnero.user.User;
import com.turnero.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserWelcomeEmailService {

    private static final Logger log = LoggerFactory.getLogger(UserWelcomeEmailService.class);

    private final BrevoTransactionalEmailClient emailClient;

    public UserWelcomeEmailService(final BrevoTransactionalEmailClient emailClient) {
        this.emailClient = emailClient;
    }

    public void sendWelcomeEmail(final User user) {
        if (this.emailClient.sendEmail(
                user.getEmail(),
                null,
                "Bienvenido/a a HallarTurno",
                this.plainTextContent(user),
                this.htmlContent(user)
        )) {
            log.info("welcome email sent userId={} email={}", user.getId(), user.getEmail());
        }
    }

    private String plainTextContent(final User user) {
        final String accountType = this.accountType(user);
        return """
                Hola,

                Tu cuenta de HallarTurno fue creada correctamente como %s.

                Ya podes iniciar sesion y empezar a usar la plataforma.

                Equipo de HallarTurno
                """.formatted(accountType);
    }

    private String htmlContent(final User user) {
        final String accountType = this.accountType(user);
        final String managementText = user.getRoles().contains(UserRole.BUSINESS)
                ? "empezar a gestionar tus turnos desde la plataforma"
                : "empezar a reservar turnos desde la plataforma";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Bienvenido/a a HallarTurno</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f6f8; font-family:Arial, Helvetica, sans-serif; color:#222222;">
                  <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f8; padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px; background-color:#ffffff; border-radius:14px; overflow:hidden;">
                          <tr>
                            <td align="center" style="background-color:#2563eb; padding:28px 20px; color:#ffffff;">
                              <div style="font-size:30px; font-weight:700;">HallarTurno</div>
                              <div style="font-size:14px; margin-top:6px; opacity:0.9;">Tus turnos, más simples.</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 36px;">
                              <h1 style="margin:0 0 20px; font-size:26px; line-height:1.3; color:#111827;">¡Bienvenido/a a HallarTurno!</h1>
                              <p style="margin:0 0 18px; font-size:16px; line-height:1.6; color:#4b5563;">Hola,</p>
                              <p style="margin:0 0 18px; font-size:16px; line-height:1.6; color:#4b5563;">
                                Tu cuenta de <strong style="color:#111827;">HallarTurno</strong>
                                fue creada correctamente como %s.
                              </p>
                              <p style="margin:0 0 28px; font-size:16px; line-height:1.6; color:#4b5563;">
                                Ya podés iniciar sesión y %s.
                              </p>
                              <table cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td align="center" bgcolor="#2563eb" style="border-radius:8px;">
                                    <a href="https://hallarturnofront-production.up.railway.app/auth/login" target="_blank" style="display:inline-block; padding:14px 24px; font-size:16px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:8px;">
                                      Iniciar sesión
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:32px 0 8px; font-size:16px; line-height:1.6; color:#4b5563;">Gracias por confiar en nosotros.</p>
                              <p style="margin:0; font-size:16px; line-height:1.6; color:#111827;"><strong>Equipo de HallarTurno</strong></p>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="background-color:#f8fafc; padding:22px 24px; border-top:1px solid #e5e7eb;">
                              <p style="margin:0 0 6px; font-size:13px; color:#6b7280;">Este es un correo automático enviado por HallarTurno.</p>
                              <p style="margin:0; font-size:12px; color:#9ca3af;">© HallarTurno</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(accountType, managementText);
    }

    private String accountType(final User user) {
        return user.getRoles().contains(UserRole.BUSINESS) ? "negocio" : "cliente";
    }

}
