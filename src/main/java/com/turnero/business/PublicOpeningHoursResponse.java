package com.turnero.business;

import com.turnero.branch.BranchOpeningInterval;
import com.turnero.branch.OpeningIntervalResponse;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record PublicOpeningHoursResponse(
        DayOfWeek day,
        List<OpeningIntervalResponse> timeRanges
) {
    static List<PublicOpeningHoursResponse> from(List<BranchOpeningInterval> intervals) {
        Map<DayOfWeek, List<OpeningIntervalResponse>> byDay = intervals.stream()
                .collect(Collectors.groupingBy(
                        BranchOpeningInterval::getDayOfWeek,
                        Collectors.mapping(interval -> new OpeningIntervalResponse(
                                interval.getOpensAt(), interval.getClosesAt()), Collectors.toList())
                ));
        return Arrays.stream(DayOfWeek.values())
                .map(day -> new PublicOpeningHoursResponse(day, byDay.getOrDefault(day, List.of())))
                .toList();
    }
}
