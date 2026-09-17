package com.turnero.business;

import java.util.UUID;

public record BusinessPublicProfileResponse(
        UUID businessId,
        String publicDescription,
        String aboutUs,
        String whatsapp,
        String instagram,
        String logoImageKey,
        String coverImageKey
) {
    static BusinessPublicProfileResponse from(Business business) {
        return new BusinessPublicProfileResponse(
                business.getId(),
                business.getPublicDescription(),
                business.getAboutUs(),
                business.getWhatsapp(),
                business.getInstagram(),
                business.getLogoImageKey(),
                business.getCoverImageKey()
        );
    }
}
