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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int DEFAULT_MAX_SLOTS_PER_SERVICE = 10;
    private static final int MAX_SLOTS_PER_SERVICE = 50;

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final AvailabilityService availabilityService;

    public PublicAvailabilityService(
            final ServiceOfferingRepository serviceOfferingRepository,
            final BusinessRepository businessRepository,
            final BranchRepository branchRepository,
            final AvailabilityService availabilityService
    ) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public PublicAvailabilityPageResponse search(
            final String text,
            final String service,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo,
            final String locality,
            final UUID businessId,
            final UUID branchId,
            final int page,
            final int size,
            final int offset,
            final Integer limit,
            final int maxSlotsPerService
    ) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Availability date is required");
        }
        final SearchCriteria criteria = normalizeCriteria(
                text,
                service,
                page,
                size,
                offset,
                limit,
                maxSlotsPerService,
                locality
        );
        final Branch branchFilter = findBranchFilter(branchId, businessId, criteria.locality());
        if (branchId != null && branchFilter == null) {
            return emptyResponse(criteria);
        }
        final UUID effectiveBusinessId = branchFilter == null ? businessId : branchFilter.getBusiness().getId();
        final var businesses = businessRepository.searchPublicAvailabilityBusinesses(
                criteria.hasText(),
                criteria.textPattern(),
                criteria.hasLocality(),
                criteria.localityParameter(),
                effectiveBusinessId != null,
                effectiveBusinessId,
                BusinessStatus.ACTIVE,
                BranchStatus.ACTIVE,
                ServiceOfferingStatus.ACTIVE,
                PageRequest.of(criteria.page(), criteria.size())
        );
        final List<UUID> businessIds = businesses.getContent().stream()
                .map(Business::getId)
                .toList();
        final List<ServiceOffering> offerings = findOfferings(businessIds, criteria);
        final Map<UUID, List<Branch>> branchesByBusiness = findBranchesByBusiness(businessIds, criteria.locality());
        final List<ServiceAvailabilityOption> availabilityOptions = findAvailabilityOptions(
                offerings,
                branchesByBusiness,
                branchFilter,
                criteria.locality(),
                date,
                startsFrom,
                startsTo
        );
        final int totalAvailableSlots = availabilityOptions.stream()
                .mapToInt(option -> option.slots().size())
                .sum();
        final List<ServiceAvailabilityOption> serviceOptions = availabilityOptions.stream()
                .filter(option -> !option.slots().isEmpty())
                .toList();
        final int totalMatchingServices = serviceOptions.size();
        final int toIndex = pageEnd(totalMatchingServices, criteria.offset(), criteria.limit());
        final List<ServiceAvailabilityOption> paginatedServices = pageItems(serviceOptions, criteria.offset(), toIndex);

        return new PublicAvailabilityPageResponse(
                criteria.page(),
                criteria.size(),
                criteria.offset(),
                criteria.limit(),
                businesses.getTotalElements(),
                businesses.getTotalPages(),
                totalMatchingServices,
                totalAvailableSlots,
                hasMore(toIndex, totalMatchingServices),
                toBusinessResponses(paginatedServices, criteria.maxSlotsPerService())
        );
    }

    @Transactional(readOnly = true)
    public PublicAvailabilitySlotsPageResponse findSlots(
            final UUID serviceOfferingId,
            final UUID branchId,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo,
            final int offset,
            final int limit
    ) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Availability date is required");
        }
        final Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        final ServiceOffering offering = serviceOfferingRepository.findById(serviceOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
        assertPublicAvailabilityCandidate(offering, branch);

        final int normalizedOffset = Math.max(offset, 0);
        final int normalizedLimit = normalizePositive(limit, DEFAULT_LIMIT, MAX_LIMIT);
        final List<PublicAvailabilitySlotResponse> slots = availabilityService
                .findAvailableSlots(branchId, serviceOfferingId, date)
                .stream()
                .filter(slot -> withinRequestedRange(slot, startsFrom, startsTo))
                .map(this::toPublicSlot)
                .toList();
        final int toIndex = (int) Math.min(slots.size(), (long) normalizedOffset + normalizedLimit);
        final List<PublicAvailabilitySlotResponse> paginatedSlots = normalizedOffset >= slots.size()
                ? List.of()
                : List.copyOf(slots.subList(normalizedOffset, toIndex));

        return new PublicAvailabilitySlotsPageResponse(
                serviceOfferingId,
                branchId,
                normalizedOffset,
                normalizedLimit,
                slots.size(),
                toIndex < slots.size(),
                paginatedSlots
        );
    }

    private Map<UUID, List<Branch>> findBranchesByBusiness(final List<UUID> businessIds, final String locality) {
        if (businessIds.isEmpty()) {
            return Map.of();
        }
        final Map<UUID, List<Branch>> branchesByBusiness = branchRepository.findPublicActiveBranchesForBusinesses(
                        businessIds,
                        BranchStatus.ACTIVE,
                        locality != null,
                        locality == null ? "" : locality
                ).stream()
                .collect(Collectors.groupingBy(
                        branch -> branch.getBusiness().getId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                ));
        return Collections.unmodifiableMap(branchesByBusiness);
    }

    private List<Branch> candidateBranches(
            final ServiceOffering offering,
            final Map<UUID, List<Branch>> branchesByBusiness,
            final Branch branchFilter,
            final String locality
    ) {
        if (offering.getBranch() != null) {
            final Branch branch = offering.getBranch();
            if (branch.getStatus() != BranchStatus.ACTIVE
                    || !matchesBranch(branch, branchFilter)
                    || !matchesLocality(branch, locality)) {
                return List.of();
            }
            return List.of(branch);
        }
        if (branchFilter != null) {
            return List.of(branchFilter);
        }
        return branchesByBusiness.getOrDefault(offering.getBusiness().getId(), List.of());
    }

    private Branch findBranchFilter(final UUID branchId, final UUID businessId, final String locality) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId)
                .filter(branch -> branch.getStatus() == BranchStatus.ACTIVE)
                .filter(branch -> branch.getBusiness().getStatus() == BusinessStatus.ACTIVE)
                .filter(branch -> businessId == null || branch.getBusiness().getId().equals(businessId))
                .filter(branch -> matchesLocality(branch, locality))
                .orElse(null);
    }

    private boolean withinRequestedRange(
            final AvailabilitySlotResponse slot,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        return (startsFrom == null || !slot.startsAt().isBefore(startsFrom))
                && (startsTo == null || !slot.startsAt().isAfter(startsTo));
    }

    private void assertPublicAvailabilityCandidate(final ServiceOffering offering, final Branch branch) {
        if (offering.getStatus() != ServiceOfferingStatus.ACTIVE
                || offering.getBusiness().getStatus() != BusinessStatus.ACTIVE
                || branch.getStatus() != BranchStatus.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Availability target not found");
        }
        if (!branch.getBusiness().getId().equals(offering.getBusiness().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service offering does not belong to the branch business");
        }
        if (offering.getBranch() != null && !offering.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch-specific service offering does not belong to the branch");
        }
    }

    private SearchCriteria normalizeCriteria(
            final String text,
            final String service,
            final int page,
            final int size,
            final int offset,
            final Integer limit,
            final int maxSlotsPerService,
            final String locality
    ) {
        final String normalizedText = normalizeSearchText(service == null || service.isBlank() ? text : service);
        final String normalizedLocality = normalizeText(locality);
        final int normalizedLimit = normalizePositive(
                limit == null ? DEFAULT_LIMIT : limit,
                DEFAULT_LIMIT,
                MAX_LIMIT
        );
        return new SearchCriteria(
                normalizedText,
                normalizedText == null ? "" : "%" + normalizedText + "%",
                normalizedLocality,
                normalizedLocality == null ? "" : normalizedLocality,
                Math.max(page, 0),
                normalizePositive(size, DEFAULT_SIZE, MAX_SIZE),
                Math.max(offset, 0),
                normalizedLimit,
                normalizePositive(maxSlotsPerService, DEFAULT_MAX_SLOTS_PER_SERVICE, MAX_SLOTS_PER_SERVICE)
        );
    }

    private List<ServiceOffering> findOfferings(final List<UUID> businessIds, final SearchCriteria criteria) {
        if (businessIds.isEmpty()) {
            return List.of();
        }
        return serviceOfferingRepository.findPublicActiveOfferingsForBusinesses(
                businessIds,
                criteria.hasText(),
                criteria.textPattern(),
                ServiceOfferingStatus.ACTIVE
        );
    }

    private List<ServiceAvailabilityOption> findAvailabilityOptions(
            final List<ServiceOffering> offerings,
            final Map<UUID, List<Branch>> branchesByBusiness,
            final Branch branchFilter,
            final String locality,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        return offerings.stream()
                .flatMap(offering -> candidateBranches(offering, branchesByBusiness, branchFilter, locality).stream()
                        .map(branch -> toAvailabilityOption(offering, branch, date, startsFrom, startsTo)))
                .toList();
    }

    private ServiceAvailabilityOption toAvailabilityOption(
            final ServiceOffering offering,
            final Branch branch,
            final LocalDate date,
            final LocalTime startsFrom,
            final LocalTime startsTo
    ) {
        final List<AvailabilitySlotResponse> slots = availabilityService
                .findAvailableSlots(branch.getId(), offering.getId(), date)
                .stream()
                .filter(slot -> withinRequestedRange(slot, startsFrom, startsTo))
                .toList();
        return new ServiceAvailabilityOption(offering, branch, slots);
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

    private boolean hasMore(final int toIndex, final int total) {
        return toIndex < total;
    }

    private PublicAvailabilityPageResponse emptyResponse(final SearchCriteria criteria) {
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

    private List<PublicAvailabilityBusinessResponse> toBusinessResponses(
            final List<ServiceAvailabilityOption> serviceOptions,
            final int maxSlotsPerService
    ) {
        return serviceOptions.stream()
                .map(option -> toServiceSlots(option, maxSlotsPerService))
                .collect(Collectors.groupingBy(
                        serviceSlots -> serviceSlots.offering().getBusiness().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(this::toBusinessResponse)
                .toList();
    }

    private ServiceSlots toServiceSlots(final ServiceAvailabilityOption option, final int maxSlotsPerService) {
        return new ServiceSlots(
                option.offering(),
                option.branch(),
                option.slots().stream()
                        .limit(maxSlotsPerService)
                        .map(this::toPublicSlot)
                        .toList()
        );
    }

    private PublicAvailabilityBusinessResponse toBusinessResponse(final List<ServiceSlots> serviceSlots) {
        final Business business = serviceSlots.getFirst().offering().getBusiness();
        final List<PublicAvailabilityBranchResponse> branches = serviceSlots.stream()
                .collect(Collectors.groupingBy(
                        serviceSlot -> serviceSlot.branch().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(this::toBranchResponse)
                .toList();
        return new PublicAvailabilityBusinessResponse(
                business.getId(),
                business.getName(),
                business.getShortDescription(),
                business.getSlug(),
                branches
        );
    }

    private PublicAvailabilityBranchResponse toBranchResponse(final List<ServiceSlots> serviceSlots) {
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
                serviceSlots.stream().map(this::toServiceResponse).toList()
        );
    }

    private PublicAvailabilityServiceResponse toServiceResponse(final ServiceSlots serviceSlots) {
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

    private PublicAvailabilitySlotResponse toPublicSlot(final AvailabilitySlotResponse slot) {
        return new PublicAvailabilitySlotResponse(
                slot.startsAt(),
                slot.endsAt(),
                slot.resourceId(),
                slot.resourceName()
        );
    }

    private boolean matchesLocality(final Branch branch, final String locality) {
        return locality == null || branch.getLocality().toLowerCase().equals(locality);
    }

    private boolean matchesBranch(final Branch branch, final Branch branchFilter) {
        return branchFilter == null || branch.getId().equals(branchFilter.getId());
    }

    private String normalizeText(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeSearchText(final String value) {
        final String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private int normalizePositive(final int requested, final int defaultValue, final int maxValue) {
        if (requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, maxValue);
    }

    private record ServiceAvailabilityOption(
            ServiceOffering offering,
            Branch branch,
            List<AvailabilitySlotResponse> slots
    ) {
    }

    private record ServiceSlots(
            ServiceOffering offering,
            Branch branch,
            List<PublicAvailabilitySlotResponse> slots
    ) {
    }

    private record SearchCriteria(
            String text,
            String textPattern,
            String locality,
            String localityParameter,
            int page,
            int size,
            int offset,
            int limit,
            int maxSlotsPerService
    ) {

        private boolean hasText() {
            return text != null;
        }

        private boolean hasLocality() {
            return locality != null;
        }
    }
}
