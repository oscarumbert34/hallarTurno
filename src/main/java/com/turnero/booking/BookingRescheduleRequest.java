package com.turnero.booking;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingRescheduleRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        UUID resourceId
) {
}
