package com.turnero.marketplace;

import java.util.List;

public record PublicAvailabilityPageResponse(
        int page,
        int size,
        int offset,
        int limit,
        long totalElements,
        int totalPages,
        int totalMatchingServices,
        int totalAvailableSlots,
        boolean hasMore,
        List<PublicAvailabilityBusinessResponse> results
) {
}
