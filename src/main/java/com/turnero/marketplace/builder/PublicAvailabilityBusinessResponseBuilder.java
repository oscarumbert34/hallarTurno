package com.turnero.marketplace.builder;

import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.business.Business;
import com.turnero.marketplace.dto.PublicAvailabilityBranchResponse;
import com.turnero.marketplace.dto.PublicAvailabilityBusinessResponse;
import com.turnero.marketplace.dto.PublicAvailabilityServiceResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotResponse;
import com.turnero.marketplace.dto.ServiceSlots;
import com.turnero.service.ServiceOffering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class PublicAvailabilityBusinessResponseBuilder {

    private PublicAvailabilityBusinessResponseBuilder() {
    }

    public static PublicAvailabilityBusinessResponse toBusinessResponse(final List<ServiceSlots> serviceSlots) {
        final Business business = serviceSlots.getFirst().offering().getBusiness();
        final List<PublicAvailabilityBranchResponse> branches = serviceSlots.stream()
                .collect(Collectors.groupingBy(
                        serviceSlot -> serviceSlot.branch().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(PublicAvailabilityBusinessResponseBuilder::toBranchResponse)
                .toList();
        return new PublicAvailabilityBusinessResponse(
                business.getId(),
                business.getName(),
                business.getShortDescription(),
                business.getSlug(),
                branches
        );
    }

    static PublicAvailabilityBranchResponse toBranchResponse(final List<ServiceSlots> serviceSlots) {
        final Branch branch = serviceSlots.getFirst().branch();
        return new PublicAvailabilityBranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getLocality(),
                branch.getProvince(),
                branch.getCountry(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getZoneId(),
                serviceSlots.stream().map(PublicAvailabilityBusinessResponseBuilder::toServiceResponse).toList()
        );
    }

    static PublicAvailabilityServiceResponse toServiceResponse(final ServiceSlots serviceSlots) {
        final ServiceOffering offering = serviceSlots.offering();
        return new PublicAvailabilityServiceResponse(
                offering.getId(),
                offering.getName(),
                offering.getDescription(),
                offering.getDurationMinutes(),
                offering.getPrice(),
                offering.getCurrency(),
                serviceSlots.slots()
        );
    }

    public static PublicAvailabilitySlotResponse toPublicSlot(final AvailabilitySlotResponse slot) {
        return new PublicAvailabilitySlotResponse(
                slot.startsAt(),
                slot.endsAt(),
                slot.resourceId(),
                slot.resourceName()
        );
    }
}
