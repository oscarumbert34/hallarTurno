package com.turnero.business;

import jakarta.validation.constraints.NotNull;

public record BusinessConfigurationRequest(
        @NotNull
        Boolean weeklyBookingCopyEnabled,
        Boolean depositEnabled,
        Boolean appointmentConfirmationEnabled
) {
    public BusinessConfigurationRequest(Boolean weeklyBookingCopyEnabled) {
        this(weeklyBookingCopyEnabled, null, null);
    }

    public BusinessConfigurationRequest(Boolean weeklyBookingCopyEnabled, Boolean depositEnabled) {
        this(weeklyBookingCopyEnabled, depositEnabled, null);
    }
}
