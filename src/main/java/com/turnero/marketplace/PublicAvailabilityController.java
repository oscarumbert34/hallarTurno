package com.turnero.marketplace;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/public")
public class PublicAvailabilityController {

    private final PublicAvailabilityService availabilityService;

    public PublicAvailabilityController(PublicAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public PublicAvailabilityPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String service,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startsFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startsTo,
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) UUID businessId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(required = false) @Min(1) @Max(10) Integer limit,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int maxSlotsPerService
    ) {
        return availabilityService.search(
                q,
                service,
                date,
                startsFrom,
                startsTo,
                locality,
                businessId,
                page,
                size,
                offset,
                limit,
                maxSlotsPerService
        );
    }

    @GetMapping("/availability/{serviceOfferingId}/slots")
    public PublicAvailabilitySlotsPageResponse slots(
            @PathVariable UUID serviceOfferingId,
            @RequestParam @NotNull UUID branchId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startsFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startsTo,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int limit
    ) {
        return availabilityService.findSlots(
                serviceOfferingId,
                branchId,
                date,
                startsFrom,
                startsTo,
                offset,
                limit
        );
    }
}
