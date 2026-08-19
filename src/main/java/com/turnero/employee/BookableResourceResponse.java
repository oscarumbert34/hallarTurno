package com.turnero.employee;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record BookableResourceResponse(
        UUID id,
        UUID branchId,
        UUID businessId,
        String visibleName,
        BookableResourceType type,
        BookableResourceStatus status,
        List<UUID> serviceOfferingIds,
        List<ResourceScheduleResponse> weeklySchedule,
        List<ResourceAbsenceResponse> absences,
        Instant createdAt,
        Instant updatedAt
) {

    static BookableResourceResponse from(BookableResource resource) {
        Map<DayOfWeek, List<ResourceIntervalResponse>> intervalsByDay = resource.getWorkingIntervals().stream()
                .collect(Collectors.groupingBy(
                        ResourceWorkingInterval::getDayOfWeek,
                        Collectors.mapping(ResourceIntervalResponse::from, Collectors.toList())
                ));
        List<ResourceScheduleResponse> weeklySchedule = Arrays.stream(DayOfWeek.values())
                .map(day -> new ResourceScheduleResponse(day, intervalsByDay.getOrDefault(day, List.of())))
                .toList();
        return new BookableResourceResponse(
                resource.getId(),
                resource.getBranch().getId(),
                resource.getBranch().getBusiness().getId(),
                resource.getVisibleName(),
                resource.getType(),
                resource.getStatus(),
                resource.getServiceOfferings().stream().map(service -> service.getId()).sorted().toList(),
                weeklySchedule,
                resource.getAbsences().stream().map(ResourceAbsenceResponse::from).toList(),
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }
}
