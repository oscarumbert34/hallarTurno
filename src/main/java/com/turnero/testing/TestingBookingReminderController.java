package com.turnero.testing;

import com.turnero.booking.BookingReminderService;
import com.turnero.common.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@RestController
@RequestMapping("/api/v1/testing/booking-reminders")
public class TestingBookingReminderController {

    private final BookingReminderService bookingReminderService;

    public TestingBookingReminderController(final BookingReminderService bookingReminderService) {
        this.bookingReminderService = bookingReminderService;
    }

    @PostMapping("/run")
    public ResponseEntity<TestingBookingReminderRunResponse> run(final Authentication authentication) {
        if (!hasAuthority(authentication, "ROLE_BUSINESS") && !hasAuthority(authentication, "ROLE_ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only business users or admins can run testing reminders");
        }
        try {
            this.bookingReminderService.sendDueReminders();
            return ResponseEntity.accepted().body(new TestingBookingReminderRunResponse(true, null));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(new TestingBookingReminderRunResponse(false, exception.getMessage()));
        }
    }

    private boolean hasAuthority(final Authentication authentication, final String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    public record TestingBookingReminderRunResponse(boolean triggered, String error) {
    }
}
