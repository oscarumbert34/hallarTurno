package com.turnero.business;

import java.util.UUID;

public record PublicBusinessResponse(
        UUID id,
        String name,
        String shortDescription,
        String phone,
        String contactEmail,
        String slug,
        BusinessStatus status
) {

    static PublicBusinessResponse from(Business business) {
        return new PublicBusinessResponse(
                business.getId(),
                business.getName(),
                business.getShortDescription(),
                business.getPhone(),
                business.getContactEmail(),
                business.getSlug(),
                business.getStatus()
        );
    }
}
