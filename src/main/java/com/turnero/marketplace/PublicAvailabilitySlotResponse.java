package com.turnero.marketplace;

import java.time.LocalTime;
import java.util.UUID;

public record PublicAvailabilitySlotResponse(
        LocalTime startsAt,
        LocalTime endsAt,
        UUID resourceId,
        String resourceName
) {
}
