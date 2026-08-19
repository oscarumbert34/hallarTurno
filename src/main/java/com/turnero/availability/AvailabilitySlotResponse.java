package com.turnero.availability;

import java.time.LocalTime;
import java.util.UUID;

public record AvailabilitySlotResponse(
        LocalTime startsAt,
        LocalTime endsAt,
        UUID resourceId,
        String resourceName
) {
}
