package com.turnero.booking;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record PublicBookingActionResponse(
        UUID appointmentId,
        String businessName,
        String serviceName,
        String date,
        String time,
        BookingStatus status,
        boolean tokenValid
) {
    static PublicBookingActionResponse from(BookingActionToken token, boolean tokenValid) {
        Booking booking = token.getBooking();
        var localStart = booking.getStartsAt().atZone(ZoneId.of(booking.getBranch().getZoneId()));
        return new PublicBookingActionResponse(
                booking.getId(),
                booking.getBusiness().getName(),
                booking.getServiceNameSnapshot(),
                localStart.toLocalDate().toString(),
                localStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                booking.getStatus(),
                tokenValid
        );
    }
}
