package com.turnero.branch;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record OpeningIntervalRequest(
        @NotNull
        @JsonAlias("opensAt")
        LocalTime start,

        @NotNull
        @JsonAlias("closesAt")
        LocalTime end
) {
}
