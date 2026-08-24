package com.turnero.marketplace;

import com.turnero.marketplace.builder.PublicAvailabilityBusinessResponseBuilder;
import com.turnero.marketplace.dto.PublicAvailabilityBusinessResponse;
import com.turnero.marketplace.dto.PublicAvailabilityPageResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotsPageResponse;
import com.turnero.marketplace.dto.SearchCriteria;
import com.turnero.marketplace.dto.ServiceAvailabilityOption;
import com.turnero.marketplace.dto.ServiceSlots;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

class PublicAvailabilityResponseAssembler {

    PublicAvailabilityPageResponse page(
            final SearchCriteria criteria,
            final long totalBusinesses,
            final int totalBusinessPages,
            final List<ServiceAvailabilityOption> availabilityOptions
    ) {
        final int totalAvailableSlots = availabilityOptions.stream()
                .mapToInt(option -> option.slots().size())
                .sum();
        final List<ServiceAvailabilityOption> serviceOptions = availabilityOptions.stream()
                .filter(option -> !option.slots().isEmpty())
                .toList();
        final int totalMatchingServices = serviceOptions.size();
        final int toIndex = this.pageEnd(totalMatchingServices, criteria.offset(), criteria.limit());
        final List<ServiceAvailabilityOption> paginatedServices = this.pageItems(serviceOptions, criteria.offset(), toIndex);

        return new PublicAvailabilityPageResponse(
                criteria.page(),
                criteria.size(),
                criteria.offset(),
                criteria.limit(),
                totalBusinesses,
                totalBusinessPages,
                totalMatchingServices,
                totalAvailableSlots,
                toIndex < totalMatchingServices,
                this.toBusinessResponses(paginatedServices, criteria.maxSlotsPerService())
        );
    }

    PublicAvailabilityPageResponse empty(final SearchCriteria criteria) {
        return new PublicAvailabilityPageResponse(
                criteria.page(),
                criteria.size(),
                criteria.offset(),
                criteria.limit(),
                0,
                0,
                0,
                0,
                false,
                List.of()
        );
    }

    PublicAvailabilitySlotsPageResponse slotsPage(
            final UUID serviceOfferingId,
            final UUID branchId,
            final int offset,
            final int limit,
            final List<PublicAvailabilitySlotResponse> slots
    ) {
        final int toIndex = (int) Math.min(slots.size(), (long) offset + limit);
        final List<PublicAvailabilitySlotResponse> paginatedSlots = offset >= slots.size()
                ? List.of()
                : List.copyOf(slots.subList(offset, toIndex));

        return new PublicAvailabilitySlotsPageResponse(
                serviceOfferingId,
                branchId,
                offset,
                limit,
                slots.size(),
                toIndex < slots.size(),
                paginatedSlots
        );
    }

    private List<PublicAvailabilityBusinessResponse> toBusinessResponses(
            final List<ServiceAvailabilityOption> serviceOptions,
            final int maxSlotsPerService
    ) {
        return serviceOptions.stream()
                .map(option -> this.toServiceSlots(option, maxSlotsPerService))
                .collect(Collectors.groupingBy(
                        serviceSlots -> serviceSlots.offering().getBusiness().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(PublicAvailabilityBusinessResponseBuilder::toBusinessResponse)
                .toList();
    }

    private ServiceSlots toServiceSlots(final ServiceAvailabilityOption option, final int maxSlotsPerService) {
        return new ServiceSlots(
                option.offering(),
                option.branch(),
                option.slots().stream()
                        .limit(maxSlotsPerService)
                        .map(PublicAvailabilityBusinessResponseBuilder::toPublicSlot)
                        .toList()
        );
    }

    private List<ServiceAvailabilityOption> pageItems(
            final List<ServiceAvailabilityOption> serviceOptions,
            final int offset,
            final int toIndex
    ) {
        if (offset >= serviceOptions.size()) {
            return List.of();
        }
        return List.copyOf(serviceOptions.subList(offset, toIndex));
    }

    private int pageEnd(final int total, final int offset, final int limit) {
        return (int) Math.min(total, (long) offset + limit);
    }
}
