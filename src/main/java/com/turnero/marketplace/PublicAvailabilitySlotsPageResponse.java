package com.turnero.marketplace;

import java.util.List;
import java.util.UUID;

public record PublicAvailabilitySlotsPageResponse(
        UUID serviceOfferingId,
        UUID branchId,
        int offset,
        int limit,
        int totalAvailableSlots,
        boolean hasMore,
        List<PublicAvailabilitySlotResponse> slots
) {
}
