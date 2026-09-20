package com.turnero.business;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String name,
        BusinessCategory category,
        String shortDescription,
        String publicDescription,
        String aboutUs,
        String whatsapp,
        String instagram,
        String logoImageKey,
        String coverImageKey,
        String phone,
        String contactEmail,
        String slug,
        BusinessStatus status,
        boolean depositEnabled,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {

    static BusinessResponse from(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getCategory(),
                business.getShortDescription(),
                business.getPublicDescription(),
                business.getAboutUs(),
                business.getWhatsapp(),
                business.getInstagram(),
                business.getLogoImageKey(),
                business.getCoverImageKey(),
                business.getPhone(),
                business.getContactEmail(),
                business.getSlug(),
                business.getStatus(),
                business.isDepositEnabled(),
                business.getOwner().getId(),
                business.getCreatedAt(),
                business.getUpdatedAt()
        );
    }
}
