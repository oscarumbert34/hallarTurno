package com.turnero.business;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        String shortDescription,
        String phone,
        String contactEmail,
        String slug,
        BusinessStatus status,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {

    static BusinessResponse from(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getShortDescription(),
                business.getPhone(),
                business.getContactEmail(),
                business.getSlug(),
                business.getStatus(),
                business.getOwner().getId(),
                business.getCreatedAt(),
                business.getUpdatedAt()
        );
    }
}
