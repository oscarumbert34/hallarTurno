package com.turnero.marketplace.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicAvailabilityServiceResponse(
        UUID id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String currency,
        List<PublicAvailabilitySlotResponse> slots
) {
}
