package com.turnero.marketplace;

import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.business.BusinessStatus;
import com.turnero.common.ApiException;
import com.turnero.availability.AvailabilityService;
import com.turnero.marketplace.dto.PublicAvailabilityPageResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotResponse;
import com.turnero.marketplace.dto.PublicAvailabilitySlotsPageResponse;
import com.turnero.marketplace.dto.SearchCriteria;
import com.turnero.marketplace.dto.ServiceAvailabilityOption;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PublicAvailabilityService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final PublicAvailabilityCriteriaNormalizer criteriaNormalizer;
    private final PublicAvailabilityBranchResolver branchResolver;
    private final PublicAvailabilityOptionFinder optionFinder;
    private final PublicAvailabilityResponseAssembler responseAssembler;

    public PublicAvailabilityService(
            final ServiceOfferingRepository serviceOfferingRepository,
            final BusinessRepository businessRepository,
            final BranchRepository branchRepository,
            final AvailabilityService availabilityService
    ) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.criteriaNormalizer = new PublicAvailabilityCriteriaNormalizer();
        this.branchResolver = new PublicAvailabilityBranchResolver(branchRepository);
        this.optionFinder = new PublicAvailabilityOptionFinder(availabilityService, branchResolver);
        this.responseAssembler = new PublicAvailabilityResponseAssembler();
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
        final SearchCriteria criteria = this.criteriaNormalizer.normalize(
                text,
                service,
                page,
                size,
                offset,
                limit,
                maxSlotsPerService,
                locality
        );
        final Branch branchFilter = this.branchResolver.findBranchFilter(branchId, businessId, criteria.locality());
        if (branchId != null && branchFilter == null) {
            return this.responseAssembler.empty(criteria);
        }

        final UUID effectiveBusinessId = branchFilter == null ? businessId : branchFilter.getBusiness().getId();
        final var businesses = this.businessRepository.searchPublicAvailabilityBusinesses(
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
        final List<ServiceOffering> offerings = this.findOfferings(businessIds, criteria);
        final Map<UUID, List<Branch>> branchesByBusiness = this.branchResolver.findBranchesByBusiness(
                businessIds,
                criteria.locality()
        );
        final List<ServiceAvailabilityOption> availabilityOptions = this.optionFinder.findAvailabilityOptions(
                offerings,
                branchesByBusiness,
                branchFilter,
                criteria.locality(),
                date,
                startsFrom,
                startsTo
        );

        return this.responseAssembler.page(
                criteria,
                businesses.getTotalElements(),
                businesses.getTotalPages(),
                availabilityOptions
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
        final Branch branch = this.branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        final ServiceOffering offering = this.serviceOfferingRepository.findById(serviceOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
        this.branchResolver.assertPublicAvailabilityCandidate(offering, branch);

        final int normalizedOffset = Math.max(offset, 0);
        final int normalizedLimit = this.criteriaNormalizer.normalizeLimit(limit);
        final List<PublicAvailabilitySlotResponse> slots = this.optionFinder.findPublicSlots(
                branchId,
                serviceOfferingId,
                date,
                startsFrom,
                startsTo
        );

        return this.responseAssembler.slotsPage(
                serviceOfferingId,
                branchId,
                normalizedOffset,
                normalizedLimit,
                slots
        );
    }

    private List<ServiceOffering> findOfferings(final List<UUID> businessIds, final SearchCriteria criteria) {
        if (businessIds.isEmpty()) {
            return List.of();
        }
        return this.serviceOfferingRepository.findPublicActiveOfferingsForBusinesses(
                businessIds,
                criteria.hasText(),
                criteria.textPattern(),
                ServiceOfferingStatus.ACTIVE
        );
    }
}
