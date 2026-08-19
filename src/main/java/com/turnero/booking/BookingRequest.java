package com.turnero.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingRequest(
        @NotNull
        UUID branchId,

        @NotNull
        UUID serviceOfferingId,

        @NotNull
        UUID resourceId,

        @NotNull
        LocalDate date,

        @NotNull
        LocalTime startsAt,

        @NotBlank
        @Size(max = 120)
        String customerName,

        @NotBlank
        @Size(max = 40)
        @Pattern(regexp = "^[0-9+()\\-\\s]{6,40}$", message = "must be a valid phone number")
        String customerPhone
) {
}
