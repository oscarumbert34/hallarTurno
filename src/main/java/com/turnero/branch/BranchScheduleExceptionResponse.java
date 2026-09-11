package com.turnero.branch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BranchScheduleExceptionResponse(UUID id, UUID branchId, LocalDate date,
                                              BranchScheduleExceptionType type, LocalTime startTime,
                                              LocalTime endTime, String reason, Instant createdAt) {
    static BranchScheduleExceptionResponse from(BranchScheduleException value) {
        return new BranchScheduleExceptionResponse(value.getId(), value.getBranch().getId(), value.getDate(),
                value.getType(), value.getStartTime(), value.getEndTime(), value.getReason(), value.getCreatedAt());
    }
}
