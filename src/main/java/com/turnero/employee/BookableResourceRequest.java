package com.turnero.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record BookableResourceRequest(
        @NotBlank
        @Size(max = 160)
        String visibleName,

        BookableResourceType type,

        BookableResourceStatus status,

        Set<UUID> serviceOfferingIds,

        @Valid
        @JsonAlias("schedule")
        List<ResourceScheduleRequest> weeklySchedule,

        @Valid
        List<ResourceAbsenceRequest> absences
) {
}
