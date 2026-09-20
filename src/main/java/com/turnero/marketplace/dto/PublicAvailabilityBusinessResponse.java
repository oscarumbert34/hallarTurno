package com.turnero.marketplace.dto;

import java.util.List;
import java.util.UUID;
import com.turnero.business.BusinessCategory;

public record PublicAvailabilityBusinessResponse(
        UUID id,
        String name,
        BusinessCategory category,
        String shortDescription,
        String slug,
        boolean depositEnabled,
        List<PublicAvailabilityBranchResponse> branches
) {
}
