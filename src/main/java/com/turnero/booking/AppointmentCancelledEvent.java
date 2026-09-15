package com.turnero.booking;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledEvent(
        UUID bookingId,
        String recipientEmail,
        String recipientName,
        String customerName,
        String serviceName,
        Instant startsAt,
        String zoneId,
        String branchName,
        String resourceName
) {

    static AppointmentCancelledEvent from(Booking booking) {
        String businessEmail = booking.getBusiness().getContactEmail();
        String recipientEmail = businessEmail == null || businessEmail.isBlank()
                ? booking.getBusiness().getOwner().getEmail()
                : businessEmail;
        return new AppointmentCancelledEvent(
                booking.getId(),
                recipientEmail,
                booking.getBusiness().getName(),
                booking.getCustomerNameSnapshot(),
                booking.getServiceNameSnapshot(),
                booking.getStartsAt(),
                booking.getBranch().getZoneId(),
                booking.getBranch().getName(),
                booking.getResourceNameSnapshot()
        );
    }
}
