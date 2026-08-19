package com.turnero.branch;

import java.time.LocalTime;

public record OpeningIntervalResponse(LocalTime opensAt, LocalTime closesAt) {

    static OpeningIntervalResponse from(BranchOpeningInterval interval) {
        return new OpeningIntervalResponse(interval.getOpensAt(), interval.getClosesAt());
    }
}
