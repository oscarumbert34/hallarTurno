package com.turnero.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceOfferingResponse(
        UUID id,
        UUID businessId,
        UUID branchId,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String currency,
        ServiceOfferingStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    static ServiceOfferingResponse from(ServiceOffering offering) {
        return new ServiceOfferingResponse(
                offering.getId(),
                offering.getBusiness().getId(),
                offering.getBranch() == null ? null : offering.getBranch().getId(),
                offering.getName(),
                offering.getDescription(),
                offering.getDurationMinutes(),
                offering.getPrice(),
                offering.getCurrency(),
                offering.getStatus(),
                offering.getCreatedAt(),
                offering.getUpdatedAt()
        );
    }
}
