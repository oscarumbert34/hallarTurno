package com.turnero.testing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TestingEmailUsageRequest(
        @Min(0) @Max(5000) int addonUsed,
        @Min(0) @Max(5000) int growthAgendaUsed
) {
}
