package com.turnero.branch;

import java.time.DayOfWeek;
import java.util.List;

public record BranchScheduleResponse(DayOfWeek dayOfWeek, List<OpeningIntervalResponse> intervals) {
}
