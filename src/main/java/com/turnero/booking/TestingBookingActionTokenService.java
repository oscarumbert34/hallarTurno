package com.turnero.booking;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("e2e & !prod")
public class TestingBookingActionTokenService {

    private final BookingRepository bookings;
    private final BookingActionTokenRepository tokens;
    private final BookingActionTokenService tokenService;
    private final OwnershipGuard ownershipGuard;

    public TestingBookingActionTokenService(
            BookingRepository bookings,
            BookingActionTokenRepository tokens,
            BookingActionTokenService tokenService,
            OwnershipGuard ownershipGuard
    ) {
        this.bookings = bookings;
        this.tokens = tokens;
        this.tokenService = tokenService;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public String issue(UUID bookingId, AuthenticatedUser actor) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        ownershipGuard.requireOwnerOrAdmin(
                booking.getBusiness(), actor, "Testing tokens can only be issued by the business owner or an admin");
        tokens.findByBookingId(bookingId).ifPresent(tokens::delete);
        tokens.flush();
        return tokenService.issueFor(booking);
    }
}
