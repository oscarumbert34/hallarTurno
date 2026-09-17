package com.turnero.business;

import com.turnero.branch.Branch;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicBusinessBranchResponse(
        UUID id,
        String name,
        String address,
        String city,
        String province,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String zoneId,
        List<PublicOpeningHoursResponse> openingHours
) {

    static PublicBusinessBranchResponse from(final Branch branch) {
        return new PublicBusinessBranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getLocality(),
                branch.getProvince(),
                branch.getCountry(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getZoneId(),
                PublicOpeningHoursResponse.from(branch.getOpeningIntervals())
        );
    }
}
