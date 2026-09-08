package com.turnero.business;

import com.turnero.service.ServiceOffering;
import java.math.BigDecimal;
import java.util.UUID;

public record PublicBranchServiceResponse(
        UUID id,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String currency
) {

    static PublicBranchServiceResponse from(final ServiceOffering offering) {
        return new PublicBranchServiceResponse(
                offering.getId(),
                offering.getName(),
                offering.getDescription(),
                offering.getDurationMinutes(),
                offering.getPrice(),
                offering.getCurrency()
        );
    }
}
