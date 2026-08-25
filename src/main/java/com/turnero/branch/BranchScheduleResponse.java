package com.turnero.branch;

import java.time.DayOfWeek;
import java.util.List;

public record BranchScheduleResponse(DayOfWeek day, List<OpeningIntervalResponse> timeRanges) {
}
