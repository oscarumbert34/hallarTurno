package com.turnero.booking;

import jakarta.validation.constraints.NotNull;

public record BookingDepositStatusRequest(
        @NotNull DepositStatus depositStatus
) {
}
