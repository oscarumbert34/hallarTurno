package com.turnero.marketplace.controller;

import com.turnero.marketplace.PublicAvailabilityService;
import com.turnero.marketplace.dto.PublicAvailabilityPageResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotsPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/public")
public class PublicAvailabilityController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SERVICE_LIMIT = 50;
    private static final int MAX_SLOTS_PER_SERVICE = 50;

    private final PublicAvailabilityService availabilityService;

    public PublicAvailabilityController(final PublicAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public PublicAvailabilityPageResponse search(
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String service,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) final LocalTime startsFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) final LocalTime startsTo,
            @RequestParam(required = false) final String locality,
            @RequestParam(required = false) final UUID businessId,
            @RequestParam(required = false) final UUID branchId,
            @RequestParam(defaultValue = "0") @Min(0) final int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(value = MAX_PAGE_SIZE, message = "Availability page size must be at most 50") final int size,
            @RequestParam(defaultValue = "0") @Min(0) final int offset,
            @RequestParam(required = false) @Min(1) @Max(value = MAX_SERVICE_LIMIT, message = "Availability limit must be at most 50") final Integer limit,
            @RequestParam(defaultValue = "10") @Min(1) @Max(value = MAX_SLOTS_PER_SERVICE, message = "Availability maxSlotsPerService must be at most 50") final int maxSlotsPerService
    ) {
        return this.availabilityService.search(
                q,
                service,
                date,
                startsFrom,
                startsTo,
                locality,
                businessId,
                branchId,
                page,
                size,
                offset,
                limit,
                maxSlotsPerService
        );
    }

    @GetMapping("/availability/{serviceOfferingId}/slots")
    public PublicAvailabilitySlotsPageResponse slots(
            @PathVariable final UUID serviceOfferingId,
            @RequestParam @NotNull final UUID branchId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) final LocalTime startsFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) final LocalTime startsTo,
            @RequestParam(defaultValue = "0") @Min(0) final int offset,
            @RequestParam(defaultValue = "10") @Min(1) @Max(value = MAX_SLOTS_PER_SERVICE, message = "Availability slots limit must be at most 50") final int limit
    ) {
        return this.availabilityService.findSlots(
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
