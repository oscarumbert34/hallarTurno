package com.turnero.business;

import java.util.UUID;

public record BusinessConfigurationResponse(
        UUID businessId,
        boolean weeklyBookingCopyEnabled
) {

    static BusinessConfigurationResponse from(BusinessConfiguration configuration) {
        return new BusinessConfigurationResponse(
                configuration.getBusiness().getId(),
                configuration.isWeeklyBookingCopyEnabled()
        );
    }
}
