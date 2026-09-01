package com.turnero.booking;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record WeeklyBookingCopyRequest(
        @NotNull
        LocalDate sourceWeekStart,

        @NotNull
        LocalDate targetWeekStart,

        UUID branchId,

        UUID resourceId,

        UUID serviceOfferingId
) {
}
