package com.turnero.business;

import jakarta.validation.constraints.NotNull;

public record BusinessConfigurationRequest(
        @NotNull
        Boolean weeklyBookingCopyEnabled
) {
}
