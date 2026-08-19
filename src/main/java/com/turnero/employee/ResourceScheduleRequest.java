package com.turnero.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.List;

public record ResourceScheduleRequest(
        @NotNull
        DayOfWeek dayOfWeek,

        @Valid
        List<ResourceIntervalRequest> intervals
) {
}
