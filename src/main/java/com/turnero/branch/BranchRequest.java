package com.turnero.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record BranchRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        @NotBlank
        @Size(max = 240)
        String address,

        @NotBlank
        @Size(max = 120)
        String locality,

        @NotBlank
        @Size(max = 120)
        String province,

        @NotBlank
        @Size(max = 120)
        String country,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude,

        @Size(max = 64)
        String zoneId,

        BranchStatus status,

        @Valid
        List<BranchScheduleRequest> weeklySchedule
) {
}
