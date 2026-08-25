package com.turnero.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.List;

public record ResourceScheduleRequest(
        @NotNull
        @JsonAlias("dayOfWeek")
        DayOfWeek day,

        @Valid
        @JsonAlias("intervals")
        List<ResourceIntervalRequest> timeRanges
) {
}
