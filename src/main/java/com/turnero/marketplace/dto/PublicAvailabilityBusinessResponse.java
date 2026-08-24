package com.turnero.marketplace.dto;

import java.util.List;
import java.util.UUID;

public record PublicAvailabilityBusinessResponse(
        UUID id,
        String name,
        String shortDescription,
        String slug,
        List<PublicAvailabilityBranchResponse> branches
) {
}
