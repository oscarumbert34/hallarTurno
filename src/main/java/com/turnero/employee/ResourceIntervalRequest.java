package com.turnero.employee;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ResourceIntervalRequest(
        @NotNull
        LocalTime startsAt,

        @NotNull
        LocalTime endsAt
) {
}
