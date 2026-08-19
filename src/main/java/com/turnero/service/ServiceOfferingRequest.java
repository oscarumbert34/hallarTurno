package com.turnero.service;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        @Size(max = 500)
        String description,

        @NotNull
        @Min(5)
        @Max(1440)
        Integer durationMinutes,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Pattern(regexp = "[A-Za-z]{3}")
        String currency,

        ServiceOfferingStatus status,

        UUID branchId
) {
}
