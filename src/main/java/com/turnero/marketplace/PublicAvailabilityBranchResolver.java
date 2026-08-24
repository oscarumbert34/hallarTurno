package com.turnero.marketplace;

import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.business.BusinessStatus;
import com.turnero.common.ApiException;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingStatus;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

class PublicAvailabilityBranchResolver {

    private final BranchRepository branchRepository;

    PublicAvailabilityBranchResolver(final BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    Branch findBranchFilter(final UUID branchId, final UUID businessId, final String locality) {
        if (branchId == null) {
            return null;
        }
        return this.branchRepository.findById(branchId)
                .filter(branch -> branch.getStatus() == BranchStatus.ACTIVE)
                .filter(branch -> branch.getBusiness().getStatus() == BusinessStatus.ACTIVE)
                .filter(branch -> businessId == null || branch.getBusiness().getId().equals(businessId))
                .filter(branch -> this.matchesLocality(branch, locality))
                .orElse(null);
    }

    Map<UUID, List<Branch>> findBranchesByBusiness(final List<UUID> businessIds, final String locality) {
        if (businessIds.isEmpty()) {
            return Map.of();
        }
        final Map<UUID, List<Branch>> branchesByBusiness = this.branchRepository.findPublicActiveBranchesForBusinesses(
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

    List<Branch> candidateBranches(
            final ServiceOffering offering,
            final Map<UUID, List<Branch>> branchesByBusiness,
            final Branch branchFilter,
            final String locality
    ) {
        if (offering.getBranch() != null) {
            final Branch branch = offering.getBranch();
            if (branch.getStatus() != BranchStatus.ACTIVE
                    || !this.matchesBranch(branch, branchFilter)
                    || !this.matchesLocality(branch, locality)) {
                return List.of();
            }
            return List.of(branch);
        }
        if (branchFilter != null) {
            return List.of(branchFilter);
        }
        return branchesByBusiness.getOrDefault(offering.getBusiness().getId(), List.of());
    }

    void assertPublicAvailabilityCandidate(final ServiceOffering offering, final Branch branch) {
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

    private boolean matchesLocality(final Branch branch, final String locality) {
        return locality == null || branch.getLocality().toLowerCase().equals(locality);
    }

    private boolean matchesBranch(final Branch branch, final Branch branchFilter) {
        return branchFilter == null || branch.getId().equals(branchFilter.getId());
    }
}
