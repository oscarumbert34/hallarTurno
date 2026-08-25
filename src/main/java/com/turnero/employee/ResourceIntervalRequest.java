package com.turnero.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ResourceIntervalRequest(
        @NotNull
        @JsonAlias("startsAt")
        LocalTime start,

        @NotNull
        @JsonAlias("endsAt")
        LocalTime end
) {
}
