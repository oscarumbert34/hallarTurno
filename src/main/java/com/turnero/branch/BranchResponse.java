package com.turnero.branch;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record BranchResponse(
        UUID id,
        UUID businessId,
        String name,
        String address,
        String locality,
        String province,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String zoneId,
        BranchStatus status,
        List<BranchScheduleResponse> weeklySchedule,
        Instant createdAt,
        Instant updatedAt
) {

    static BranchResponse from(Branch branch) {
        Map<DayOfWeek, List<OpeningIntervalResponse>> intervalsByDay = branch.getOpeningIntervals().stream()
                .collect(Collectors.groupingBy(
                        BranchOpeningInterval::getDayOfWeek,
                        Collectors.mapping(OpeningIntervalResponse::from, Collectors.toList())
                ));
        List<BranchScheduleResponse> weeklySchedule = Arrays.stream(DayOfWeek.values())
                .map(day -> new BranchScheduleResponse(day, intervalsByDay.getOrDefault(day, List.of())))
                .toList();
        return new BranchResponse(
                branch.getId(),
                branch.getBusiness().getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getLocality(),
                branch.getProvince(),
                branch.getCountry(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getZoneId(),
                branch.getStatus(),
                weeklySchedule,
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
