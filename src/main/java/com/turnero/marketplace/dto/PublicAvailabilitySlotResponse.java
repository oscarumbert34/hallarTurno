package com.turnero.marketplace.dto;

import java.time.LocalTime;
import java.util.UUID;

public record PublicAvailabilitySlotResponse(
        LocalTime startsAt,
        LocalTime endsAt,
        UUID resourceId,
        String resourceName
) {
}
