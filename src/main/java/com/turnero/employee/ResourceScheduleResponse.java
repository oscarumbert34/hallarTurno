package com.turnero.employee;

import java.time.DayOfWeek;
import java.util.List;

public record ResourceScheduleResponse(DayOfWeek day, List<ResourceIntervalResponse> timeRanges) {
}
