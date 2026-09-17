package com.turnero.business;

import java.util.List;
import java.util.UUID;

public record PublicBusinessDetailResponse(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        String publicDescription,
        String aboutUs,
        String whatsapp,
        String instagram,
        String logoUrl,
        String coverImageUrl,
        String phone,
        String email,
        boolean depositEnabled,
        List<PublicBusinessBranchResponse> branches,
        List<PublicBranchServiceResponse> services
) {
}
