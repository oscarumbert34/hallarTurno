package com.turnero.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WeeklyBookingCopyConflict(
        UUID sourceBookingId,
        LocalDate date,
        LocalTime startsAt,
        UUID branchId,
        UUID resourceId,
        UUID serviceOfferingId,
        String reason
) {
}
