package com.turnero.employee;

import java.time.DayOfWeek;
import java.util.List;

public record ResourceScheduleResponse(DayOfWeek dayOfWeek, List<ResourceIntervalResponse> intervals) {
}
