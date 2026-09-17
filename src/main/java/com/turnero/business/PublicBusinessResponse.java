package com.turnero.business;

import java.util.UUID;

public record PublicBusinessResponse(
        UUID id,
        String name,
        String shortDescription,
        String publicDescription,
        String aboutUs,
        String whatsapp,
        String instagram,
        String phone,
        String contactEmail,
        String slug,
        BusinessStatus status,
        boolean depositEnabled
) {

    static PublicBusinessResponse from(Business business) {
        return new PublicBusinessResponse(
                business.getId(),
                business.getName(),
                business.getShortDescription(),
                business.getPublicDescription(),
                business.getAboutUs(),
                business.getWhatsapp(),
                business.getInstagram(),
                business.getPhone(),
                business.getContactEmail(),
                business.getSlug(),
                business.getStatus(),
                business.isDepositEnabled()
        );
    }
}
