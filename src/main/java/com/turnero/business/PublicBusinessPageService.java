package com.turnero.business;

import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.common.ApiException;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBusinessPageService {

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;

    public PublicBusinessPageService(
            final BusinessRepository businessRepository,
            final BranchRepository branchRepository,
            final ServiceOfferingRepository serviceOfferingRepository
    ) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @Transactional(readOnly = true)
    public PublicBusinessDetailResponse findBySlug(final String slug) {
        final Business business = this.findActiveBusiness(slug);
        final List<PublicBusinessBranchResponse> branches = this.branchRepository
                .findByBusinessIdAndStatusOrderByNameAsc(business.getId(), BranchStatus.ACTIVE)
                .stream()
                .map(PublicBusinessBranchResponse::from)
                .toList();
        return new PublicBusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getSlug(),
                business.getShortDescription(),
                business.getPhone(),
                business.getContactEmail(),
                business.isDepositEnabled(),
                branches
        );
    }

    @Transactional(readOnly = true)
    public List<PublicBranchServiceResponse> findBranchServices(final String slug, final UUID branchId) {
        final Business business = this.findActiveBusiness(slug);
        final Branch branch = this.branchRepository.findById(branchId)
                .filter(candidate -> candidate.getBusiness().getId().equals(business.getId()))
                .filter(candidate -> candidate.getStatus() == BranchStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        return this.serviceOfferingRepository.findPublicActiveForBranch(
                        business.getId(),
                        branch.getId(),
                        ServiceOfferingStatus.ACTIVE
                ).stream()
                .map(PublicBranchServiceResponse::from)
                .toList();
    }

    private Business findActiveBusiness(final String slug) {
        return this.businessRepository.findBySlugAndStatus(slug, BusinessStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }
}
