package com.turnero.marketplace;

import com.turnero.availability.AvailabilityService;
import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.business.BusinessStatus;
import com.turnero.common.ApiException;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicAvailabilityService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final AvailabilityService availabilityService;

    public PublicAvailabilityService(
            ServiceOfferingRepository serviceOfferingRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            AvailabilityService availabilityService
    ) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public PublicAvailabilityPageResponse search(
            String text,
            String service,
            LocalDate date,
            LocalTime startsFrom,
            LocalTime startsTo,
            String locality,
            UUID businessId,
            int page,
            int size,
            int offset,
            Integer limit,
            int maxSlotsPerService
    ) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Availability date is required");
        }
        String normalizedText = normalizeSearchText(service == null || service.isBlank() ? text : service);
        String normalizedLocality = normalizeText(locality);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = normalizePositive(size, DEFAULT_SIZE, MAX_SIZE);
        int normalizedOffset = Math.max(offset, 0);
        int normalizedLimit = normalizePositive(
                limit == null ? maxSlotsPerService : limit,
                DEFAULT_LIMIT,
                MAX_LIMIT
        );

        String textPattern = normalizedText == null ? "" : "%" + normalizedText + "%";
        var businesses = businessRepository.searchPublicAvailabilityBusinesses(
                normalizedText != null,
                textPattern,
                normalizedLocality != null,
                normalizedLocality == null ? "" : normalizedLocality,
                businessId != null,
                businessId,
                BusinessStatus.ACTIVE,
                BranchStatus.ACTIVE,
                ServiceOfferingStatus.ACTIVE,
                PageRequest.of(normalizedPage, normalizedSize)
        );
        List<UUID> businessIds = businesses.getContent().stream()
                .map(Business::getId)
                .toList();
        List<ServiceOffering> offerings = businessIds.isEmpty()
                ? List.of()
                : serviceOfferingRepository.findPublicActiveOfferingsForBusinesses(
                        businessIds,
                        normalizedText != null,
                        textPattern,
                        ServiceOfferingStatus.ACTIVE
                );

        Map<UUID, List<Branch>> branchesByBusiness = findBranchesByBusiness(businessIds, normalizedLocality);
        Map<UUID, BusinessGroup> businessGroups = new LinkedHashMap<>();
        int totalAvailableSlots = 0;
        boolean hasMore = false;

        for (ServiceOffering offering : offerings) {
            List<Branch> candidateBranches = candidateBranches(offering, branchesByBusiness, normalizedLocality);
            for (Branch branch : candidateBranches) {
                List<AvailabilitySlotResponse> slots = availabilityService
                        .findAvailableSlots(branch.getId(), offering.getId(), date)
                        .stream()
                        .filter(slot -> withinRequestedRange(slot, startsFrom, startsTo))
                        .toList();
                totalAvailableSlots += slots.size();
                int toIndex = (int) Math.min(slots.size(), (long) normalizedOffset + normalizedLimit);
                if (toIndex < slots.size()) {
                    hasMore = true;
                }
                if (normalizedOffset >= slots.size()) {
                    continue;
                }
                slots.subList(normalizedOffset, toIndex).stream()
                        .map(this::toPublicSlot)
                        .forEach(slot -> addResult(businessGroups, offering, branch, slot));
            }
        }

        return new PublicAvailabilityPageResponse(
                normalizedPage,
                normalizedSize,
                normalizedOffset,
                normalizedLimit,
                businesses.getTotalElements(),
                businesses.getTotalPages(),
                totalAvailableSlots,
                hasMore,
                businessGroups.values().stream().map(BusinessGroup::toResponse).toList()
        );
    }

    private Map<UUID, List<Branch>> findBranchesByBusiness(List<UUID> businessIds, String locality) {
        if (businessIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findPublicActiveBranchesForBusinesses(
                        businessIds,
                        BranchStatus.ACTIVE,
                        locality != null,
                        locality == null ? "" : locality
                ).stream()
                .collect(Collectors.groupingBy(branch -> branch.getBusiness().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private List<Branch> candidateBranches(
            ServiceOffering offering,
            Map<UUID, List<Branch>> branchesByBusiness,
            String locality
    ) {
        if (offering.getBranch() != null) {
            Branch branch = offering.getBranch();
            if (branch.getStatus() != BranchStatus.ACTIVE || !matchesLocality(branch, locality)) {
                return List.of();
            }
            return List.of(branch);
        }
        return branchesByBusiness.getOrDefault(offering.getBusiness().getId(), List.of());
    }

    private boolean withinRequestedRange(
            AvailabilitySlotResponse slot,
            LocalTime startsFrom,
            LocalTime startsTo
    ) {
        return (startsFrom == null || !slot.startsAt().isBefore(startsFrom))
                && (startsTo == null || !slot.startsAt().isAfter(startsTo));
    }

    private void addResult(
            Map<UUID, BusinessGroup> businessGroups,
            ServiceOffering offering,
            Branch branch,
            PublicAvailabilitySlotResponse slot
    ) {
        Business business = offering.getBusiness();
        BusinessGroup businessGroup = businessGroups.computeIfAbsent(
                business.getId(),
                ignored -> new BusinessGroup(business)
        );
        BranchGroup branchGroup = businessGroup.branches.computeIfAbsent(
                branch.getId(),
                ignored -> new BranchGroup(branch)
        );
        branchGroup.addSlot(offering, slot);
    }

    private PublicAvailabilitySlotResponse toPublicSlot(AvailabilitySlotResponse slot) {
        return new PublicAvailabilitySlotResponse(
                slot.startsAt(),
                slot.endsAt(),
                slot.resourceId(),
                slot.resourceName()
        );
    }

    private boolean matchesLocality(Branch branch, String locality) {
        return locality == null || branch.getLocality().toLowerCase().equals(locality);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeSearchText(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private int normalizePositive(int requested, int defaultValue, int maxValue) {
        if (requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, maxValue);
    }

    private static class ServiceGroup {
        private final ServiceOffering offering;
        private final List<PublicAvailabilitySlotResponse> slots = new java.util.ArrayList<>();

        private ServiceGroup(ServiceOffering offering) {
            this.offering = Objects.requireNonNull(offering);
        }

        private void addSlot(PublicAvailabilitySlotResponse slot) {
            slots.add(slot);
        }

        private PublicAvailabilityServiceResponse toResponse() {
            return new PublicAvailabilityServiceResponse(
                    offering.getId(),
                    offering.getName(),
                    offering.getDescription(),
                    offering.getDurationMinutes(),
                    offering.getPrice(),
                    offering.getCurrency(),
                    slots
            );
        }
    }

    private static class BusinessGroup {
        private final Business business;
        private final Map<UUID, BranchGroup> branches = new LinkedHashMap<>();

        private BusinessGroup(Business business) {
            this.business = Objects.requireNonNull(business);
        }

        private PublicAvailabilityBusinessResponse toResponse() {
            return new PublicAvailabilityBusinessResponse(
                    business.getId(),
                    business.getName(),
                    business.getShortDescription(),
                    business.getSlug(),
                    branches.values().stream().map(BranchGroup::toResponse).toList()
            );
        }
    }

    private static class BranchGroup {
        private final Branch branch;
        private final Map<UUID, ServiceGroup> services = new LinkedHashMap<>();

        private BranchGroup(Branch branch) {
            this.branch = Objects.requireNonNull(branch);
        }

        private void addSlot(ServiceOffering offering, PublicAvailabilitySlotResponse slot) {
            ServiceGroup serviceGroup = services.computeIfAbsent(
                    offering.getId(),
                    ignored -> new ServiceGroup(offering)
            );
            serviceGroup.addSlot(slot);
        }

        private PublicAvailabilityBranchResponse toResponse() {
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
                    services.values().stream().map(ServiceGroup::toResponse).toList()
            );
        }
    }
}
