package com.turnero.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID customerId,
        UUID businessId,
        UUID branchId,
        UUID resourceId,
        UUID serviceOfferingId,
        Instant startsAt,
        Instant endsAt,
        String serviceName,
        String resourceName,
        String customerName,
        String customerPhone,
        Integer durationMinutes,
        BigDecimal price,
        String currency,
        BookingStatus status,
        Instant cancelledAt,
        UUID cancelledBy,
        Instant createdAt,
        Instant updatedAt
) {

    static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getCustomer() == null ? null : booking.getCustomer().getId(),
                booking.getBusiness().getId(),
                booking.getBranch().getId(),
                booking.getResource().getId(),
                booking.getServiceOffering().getId(),
                booking.getStartsAt(),
                booking.getEndsAt(),
                booking.getServiceNameSnapshot(),
                booking.getResourceNameSnapshot(),
                booking.getCustomerNameSnapshot(),
                booking.getCustomerPhoneSnapshot(),
                booking.getDurationMinutesSnapshot(),
                booking.getPriceSnapshot(),
                booking.getCurrencySnapshot(),
                booking.getStatus(),
                booking.getCancelledAt(),
                booking.getCancelledBy() == null ? null : booking.getCancelledBy().getId(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}



