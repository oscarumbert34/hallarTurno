package com.turnero.business;

import jakarta.validation.constraints.NotNull;

public record BusinessConfigurationRequest(
        @NotNull
        Boolean weeklyBookingCopyEnabled,
        Boolean depositEnabled
) {
    public BusinessConfigurationRequest(Boolean weeklyBookingCopyEnabled) {
        this(weeklyBookingCopyEnabled, null);
    }
}
