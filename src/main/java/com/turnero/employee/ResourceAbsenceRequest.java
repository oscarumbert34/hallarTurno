package com.turnero.employee;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceAbsenceRequest(
        @NotNull
        LocalDate date,

        @NotNull
        LocalTime startsAt,

        @NotNull
        LocalTime endsAt
) {
}
