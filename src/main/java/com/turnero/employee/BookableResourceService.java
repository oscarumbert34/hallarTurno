package com.turnero.employee;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class BookableResourceService {

    private final BookableResourceRepository resourceRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository offeringRepository;
    private final ResourceAvailabilityValidator validator;
    private final OwnershipGuard ownershipGuard;

    public BookableResourceService(
            BookableResourceRepository resourceRepository,
            BranchRepository branchRepository,
            ServiceOfferingRepository offeringRepository,
            ResourceAvailabilityValidator validator,
            OwnershipGuard ownershipGuard
    ) {
        this.resourceRepository = resourceRepository;
        this.branchRepository = branchRepository;
        this.offeringRepository = offeringRepository;
        this.validator = validator;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public BookableResourceResponse create(UUID branchId, BookableResourceRequest request, AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        assertOwnerOrAdmin(branch, currentUser);
        BookableResource resource = BookableResource.create(
                branch,
                request.visibleName().trim(),
                request.type() == null ? BookableResourceType.EMPLOYEE : request.type(),
                request.status() == null ? BookableResourceStatus.ACTIVE : request.status()
        );
        resource.replaceServiceOfferings(resolveServiceOfferings(request.serviceOfferingIds(), branch));
        resource.replaceWorkingIntervals(validator.validateSchedule(request.weeklySchedule()));
        resource.replaceAbsences(validator.validateAbsences(request.absences()));
        return BookableResourceResponse.from(resourceRepository.saveAndFlush(resource));
    }

    @Transactional(readOnly = true)
    public List<BookableResourceResponse> findByBranch(UUID branchId, AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        assertOwnerOrAdmin(branch, currentUser);
        return resourceRepository.findDistinctByBranchIdOrderByVisibleNameAsc(branchId).stream()
                .map(BookableResourceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookableResourceResponse get(UUID id, AuthenticatedUser currentUser) {
        BookableResource resource = findResource(id);
        assertOwnerOrAdmin(resource.getBranch(), currentUser);
        return BookableResourceResponse.from(resource);
    }

    @Transactional
    public BookableResourceResponse update(UUID id, BookableResourceRequest request, AuthenticatedUser currentUser) {
        BookableResource resource = findResource(id);
        assertOwnerOrAdmin(resource.getBranch(), currentUser);
        resource.updateDetails(
                request.visibleName().trim(),
                request.type() == null ? BookableResourceType.EMPLOYEE : request.type(),
                request.status() == null ? BookableResourceStatus.ACTIVE : request.status(),
                resolveServiceOfferings(request.serviceOfferingIds(), resource.getBranch())
        );
        resource.replaceWorkingIntervals(validator.validateSchedule(request.weeklySchedule()));
        resource.replaceAbsences(validator.validateAbsences(request.absences()));
        return BookableResourceResponse.from(resource);
    }

    @Transactional
    public BookableResourceResponse deactivate(UUID id, AuthenticatedUser currentUser) {
        BookableResource resource = findResource(id);
        assertOwnerOrAdmin(resource.getBranch(), currentUser);
        resource.deactivate();
        return BookableResourceResponse.from(resource);
    }

    private Branch findBranch(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    private BookableResource findResource(UUID id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bookable resource not found"));
    }

    private Set<ServiceOffering> resolveServiceOfferings(Set<UUID> serviceOfferingIds, Branch branch) {
        if (serviceOfferingIds == null || serviceOfferingIds.isEmpty()) {
            return Set.of();
        }
        Set<ServiceOffering> offerings = new LinkedHashSet<>();
        for (UUID serviceOfferingId : serviceOfferingIds) {
            ServiceOffering offering = offeringRepository.findById(serviceOfferingId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
            assertServiceOfferingBelongsToBranchBusiness(offering, branch);
            offerings.add(offering);
        }
        return offerings;
    }

    private void assertServiceOfferingBelongsToBranchBusiness(ServiceOffering offering, Branch branch) {
        if (!offering.getBusiness().getId().equals(branch.getBusiness().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service offering does not belong to the branch business");
        }
        if (offering.getBranch() != null && !offering.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch-specific service offering cannot be assigned to this resource");
        }
    }

    private void assertOwnerOrAdmin(Branch branch, AuthenticatedUser currentUser) {
        ownershipGuard.requireOwnerOrAdmin(branch.getBusiness(), currentUser, "Bookable resource can only be managed by the business owner or an admin");
    }
}
