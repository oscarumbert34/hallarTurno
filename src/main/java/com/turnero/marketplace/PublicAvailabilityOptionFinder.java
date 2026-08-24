package com.turnero.marketplace;

import com.turnero.availability.AvailabilityService;
import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.marketplace.builder.PublicAvailabilityBusinessResponseBuilder;
import com.turnero.marketplace.dto.PublicAvailabilitySlotResponse;
import com.turnero.marketplace.dto.ServiceAvailabilityOption;
import com.turnero.service.ServiceOffering;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PublicAvailabilityOptionFinder {

    private final AvailabilityService availabilityService;
    private final PublicAvailabilityBranchResolver branchResolver;

    PublicAvailabilityOptionFinder(
            final AvailabilityService availabilityService,
            final PublicAvailabilityBranchResolver branchResolver
    ) {
        this.availabilityService = availabilityService;
        this.branchResolver = branchResolver;
    }

    List<ServiceAvailabilityOption> findAvailabilityOptions(
            final List<ServiceOffering> offerings,
            final Map<UUID, List<Branch>> branchesByBusiness,
            final Branch branchFilter,
            final String locality,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        return offerings.stream()
                .flatMap(offering -> this.branchResolver.candidateBranches(offering, branchesByBusiness, branchFilter, locality).stream()
                        .map(branch -> this.toAvailabilityOption(offering, branch, date, startsFrom, startsTo)))
                .toList();
    }

    List<PublicAvailabilitySlotResponse> findPublicSlots(
            final UUID branchId,
            final UUID serviceOfferingId,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        return this.availabilityService.findAvailableSlots(branchId, serviceOfferingId, date)
                .stream()
                .filter(slot -> this.withinRequestedRange(slot, startsFrom, startsTo))
                .map(PublicAvailabilityBusinessResponseBuilder::toPublicSlot)
                .toList();
    }

    private ServiceAvailabilityOption toAvailabilityOption(
            final ServiceOffering offering,
            final Branch branch,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        final List<AvailabilitySlotResponse> slots = this.availabilityService
                .findAvailableSlots(branch.getId(), offering.getId(), date)
                .stream()
                .filter(slot -> this.withinRequestedRange(slot, startsFrom, startsTo))
                .toList();
        return new ServiceAvailabilityOption(offering, branch, slots);
    }

    private boolean withinRequestedRange(
            final AvailabilitySlotResponse slot,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        return (startsFrom == null || !slot.startsAt().isBefore(startsFrom))
                && (startsTo == null || !slot.startsAt().isAfter(startsTo));
    }
}
