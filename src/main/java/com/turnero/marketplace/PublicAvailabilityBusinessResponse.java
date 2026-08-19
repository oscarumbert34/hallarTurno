package com.turnero.marketplace;

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
