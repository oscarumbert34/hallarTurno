package com.turnero.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.List;

public record BranchScheduleRequest(
        @NotNull
        DayOfWeek dayOfWeek,

        @Valid
        List<OpeningIntervalRequest> intervals
) {
}
