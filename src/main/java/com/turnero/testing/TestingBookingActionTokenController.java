package com.turnero.testing;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.booking.TestingBookingActionTokenService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("e2e & !prod")
@RequestMapping("/api/v1/testing/bookings/{bookingId}/action-token")
public class TestingBookingActionTokenController {

    private final TestingBookingActionTokenService service;

    public TestingBookingActionTokenController(TestingBookingActionTokenService service) {
        this.service = service;
    }

    @PostMapping
    TestingBookingActionTokenResponse issue(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        return new TestingBookingActionTokenResponse(service.issue(bookingId, actor));
    }

    record TestingBookingActionTokenResponse(String token) {}
}
