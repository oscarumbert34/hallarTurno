package com.turnero.marketplace;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicAvailabilityBranchResponse(
        UUID id,
        String name,
        String address,
        String locality,
        String province,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String zoneId,
        List<PublicAvailabilityServiceResponse> services
) {
}
