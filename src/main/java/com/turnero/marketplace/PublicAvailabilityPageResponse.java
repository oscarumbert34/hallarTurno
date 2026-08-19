package com.turnero.marketplace;

import java.util.List;

public record PublicAvailabilityPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<PublicAvailabilityBusinessResponse> results
) {
}
