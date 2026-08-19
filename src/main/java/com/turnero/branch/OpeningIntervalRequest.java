package com.turnero.branch;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record OpeningIntervalRequest(
        @NotNull
        LocalTime opensAt,

        @NotNull
        LocalTime closesAt
) {
}
