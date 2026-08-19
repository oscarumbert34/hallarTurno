package com.turnero.service;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository offeringRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingValidator validator;
    private final OwnershipGuard ownershipGuard;

    public ServiceOfferingService(
            ServiceOfferingRepository offeringRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            ServiceOfferingValidator validator,
            OwnershipGuard ownershipGuard
    ) {
        this.offeringRepository = offeringRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.validator = validator;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public ServiceOfferingResponse create(
            UUID businessId,
            ServiceOfferingRequest request,
            AuthenticatedUser currentUser
    ) {
        Business business = findBusiness(businessId);
        assertOwnerOrAdmin(business, currentUser);
        Branch branch = resolveBranch(request.branchId(), business);
        ServiceOffering offering = ServiceOffering.create(
                business,
                branch,
                request.name().trim(),
                blankToNull(request.description()),
                request.durationMinutes(),
                normalizePrice(request.price()),
                validator.normalizeCurrency(request.currency()),
                request.status() == null ? ServiceOfferingStatus.ACTIVE : request.status()
        );
        return ServiceOfferingResponse.from(offeringRepository.saveAndFlush(offering));
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> findPublicByBusiness(UUID businessId) {
        Business business = findBusiness(businessId);
        if (business.getStatus() != com.turnero.business.BusinessStatus.ACTIVE) {
            return List.of();
        }
        return offeringRepository.findByBusinessIdAndStatusOrderByNameAsc(businessId, ServiceOfferingStatus.ACTIVE).stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> findByBusiness(UUID businessId, AuthenticatedUser currentUser) {
        Business business = findBusiness(businessId);
        assertOwnerOrAdmin(business, currentUser);
        return offeringRepository.findByBusinessIdOrderByNameAsc(businessId).stream()
                .map(ServiceOfferingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse get(UUID id, AuthenticatedUser currentUser) {
        ServiceOffering offering = findOffering(id);
        assertOwnerOrAdmin(offering.getBusiness(), currentUser);
        return ServiceOfferingResponse.from(offering);
    }

    @Transactional
    public ServiceOfferingResponse update(
            UUID id,
            ServiceOfferingRequest request,
            AuthenticatedUser currentUser
    ) {
        ServiceOffering offering = findOffering(id);
        assertOwnerOrAdmin(offering.getBusiness(), currentUser);
        Branch branch = resolveBranch(request.branchId(), offering.getBusiness());
        offering.updateDetails(
                branch,
                request.name().trim(),
                blankToNull(request.description()),
                request.durationMinutes(),
                normalizePrice(request.price()),
                validator.normalizeCurrency(request.currency()),
                request.status() == null ? ServiceOfferingStatus.ACTIVE : request.status()
        );
        return ServiceOfferingResponse.from(offering);
    }

    @Transactional
    public ServiceOfferingResponse deactivate(UUID id, AuthenticatedUser currentUser) {
        ServiceOffering offering = findOffering(id);
        assertOwnerOrAdmin(offering.getBusiness(), currentUser);
        offering.deactivate();
        return ServiceOfferingResponse.from(offering);
    }

    private Business findBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    private ServiceOffering findOffering(UUID id) {
        return offeringRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
    }

    private Branch resolveBranch(UUID branchId, Business business) {
        if (branchId == null) {
            return null;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        if (!branch.getBusiness().getId().equals(business.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch does not belong to the business");
        }
        return branch;
    }

    private void assertOwnerOrAdmin(Business business, AuthenticatedUser currentUser) {
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Service offering can only be managed by the business owner or an admin");
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        try {
            return validator.normalizePrice(price);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Price supports at most two decimal places");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
