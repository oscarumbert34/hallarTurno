package com.turnero.employee;

import java.time.LocalTime;

public record ResourceIntervalResponse(LocalTime startsAt, LocalTime endsAt) {

    static ResourceIntervalResponse from(ResourceWorkingInterval interval) {
        return new ResourceIntervalResponse(interval.getStartsAt(), interval.getEndsAt());
    }
}
