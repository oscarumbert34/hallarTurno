package com.turnero.business;

import java.util.UUID;

public record BusinessConfigurationResponse(
        UUID businessId,
        boolean weeklyBookingCopyEnabled,
        boolean depositEnabled,
        boolean appointmentConfirmationEnabled,
        boolean internalBookingCreation
) {

    static BusinessConfigurationResponse from(BusinessConfiguration configuration) {
        return new BusinessConfigurationResponse(
                configuration.getBusiness().getId(),
                configuration.isWeeklyBookingCopyEnabled(),
                configuration.getBusiness().isDepositEnabled(),
                configuration.isAppointmentConfirmationEnabled(),
                configuration.isInternalBookingCreation()
        );
    }
}
