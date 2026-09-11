package com.turnero.booking;

import com.turnero.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingActionTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ObjectProvider<BookingActionTokenRepository> repositoryProvider;
    private final Clock clock;

    public BookingActionTokenService(ObjectProvider<BookingActionTokenRepository> repositoryProvider, Clock clock) {
        this.repositoryProvider = repositoryProvider;
        this.clock = clock;
    }

    String issueFor(Booking booking) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository().save(new BookingActionToken(booking, hash(rawToken), booking.getStartsAt()));
        return rawToken;
    }

    @Transactional
    void discard(String rawToken) {
        repository().findByTokenHash(hash(rawToken)).ifPresent(repository()::delete);
    }

    @Transactional
    void markNotificationSent(String rawToken) {
        repository().findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.getBooking().markReminderSent(clock.instant()));
    }

    @Transactional(readOnly = true)
    public PublicBookingActionResponse get(String rawToken) {
        BookingActionToken token = repository().findByTokenHash(hashValidated(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment action token not found"));
        return PublicBookingActionResponse.from(token, isUsable(token, clock.instant()));
    }

    @Transactional
    public PublicBookingActionResponse confirm(String rawToken) {
        BookingActionToken token = requireUsableForUpdate(rawToken);
        Booking booking = token.getBooking();
        if (booking.getStatus() != BookingStatus.PENDING_CONFIRMATION) {
            throw alreadyManaged();
        }
        booking.confirm();
        token.markUsed(clock.instant());
        return PublicBookingActionResponse.from(token, false);
    }

    @Transactional
    public PublicBookingActionResponse cancel(String rawToken) {
        BookingActionToken token = requireUsableForUpdate(rawToken);
        Booking booking = token.getBooking();
        if (booking.getStatus() != BookingStatus.PENDING_CONFIRMATION) {
            throw alreadyManaged();
        }
        booking.cancel(null, clock.instant());
        token.markUsed(clock.instant());
        return PublicBookingActionResponse.from(token, false);
    }

    private BookingActionToken requireUsableForUpdate(String rawToken) {
        BookingActionToken token = repository().findByTokenHashForUpdate(hashValidated(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment action token not found"));
        if (token.isUsed()) {
            throw alreadyManaged();
        }
        if (!clock.instant().isBefore(token.getExpiresAt())) {
            throw new ApiException(HttpStatus.GONE, "Appointment action token expired");
        }
        return token;
    }

    private boolean isUsable(BookingActionToken token, Instant now) {
        return !token.isUsed()
                && now.isBefore(token.getExpiresAt())
                && token.getBooking().getStatus() == BookingStatus.PENDING_CONFIRMATION;
    }

    private ApiException alreadyManaged() {
        return new ApiException(HttpStatus.CONFLICT, "Appointment has already been managed");
    }

    private String hashValidated(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Appointment action token not found");
        }
        return hash(rawToken);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private BookingActionTokenRepository repository() {
        return repositoryProvider.getObject();
    }
}
