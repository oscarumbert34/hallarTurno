package com.turnero.branch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record BranchScheduleExceptionRequest(
        @NotNull LocalDate date,
        @NotNull BranchScheduleExceptionType type,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 500) String reason
) {
}
