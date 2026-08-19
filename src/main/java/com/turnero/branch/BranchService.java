package com.turnero.branch;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.util.List;
import java.util.UUID;
import java.time.ZoneId;
import java.time.DateTimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final BranchScheduleValidator scheduleValidator;
    private final String defaultZoneId;
    private final OwnershipGuard ownershipGuard;

    public BranchService(
            BranchRepository branchRepository,
            BusinessRepository businessRepository,
            BranchScheduleValidator scheduleValidator,
            OwnershipGuard ownershipGuard,
            @Value("${availability.default-zone-id:America/Argentina/Buenos_Aires}") String defaultZoneId
    ) {
        this.branchRepository = branchRepository;
        this.businessRepository = businessRepository;
        this.scheduleValidator = scheduleValidator;
        this.defaultZoneId = defaultZoneId;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public BranchResponse create(UUID businessId, BranchRequest request, AuthenticatedUser currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        assertOwnerOrAdmin(business, currentUser);
        Branch branch = Branch.create(
                business,
                request.name().trim(),
                request.address().trim(),
                request.locality().trim(),
                request.province().trim(),
                request.country().trim(),
                request.latitude(),
                request.longitude(),
                request.status() == null ? BranchStatus.ACTIVE : request.status(),
                normalizeZoneId(request.zoneId())
        );
        branch.replaceOpeningIntervals(scheduleValidator.validate(request.weeklySchedule()));
        return BranchResponse.from(branchRepository.saveAndFlush(branch));
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> findByBusiness(UUID businessId, AuthenticatedUser currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        assertOwnerOrAdmin(business, currentUser);
        return branchRepository.findDistinctByBusinessIdOrderByNameAsc(businessId).stream()
                .map(BranchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse get(UUID id, AuthenticatedUser currentUser) {
        Branch branch = findBranch(id);
        assertOwnerOrAdmin(branch.getBusiness(), currentUser);
        return BranchResponse.from(branch);
    }

    @Transactional
    public BranchResponse update(UUID id, BranchRequest request, AuthenticatedUser currentUser) {
        Branch branch = findBranch(id);
        assertOwnerOrAdmin(branch.getBusiness(), currentUser);
        branch.updateDetails(
                request.name().trim(),
                request.address().trim(),
                request.locality().trim(),
                request.province().trim(),
                request.country().trim(),
                request.latitude(),
                request.longitude(),
                request.status() == null ? BranchStatus.ACTIVE : request.status(),
                normalizeZoneId(request.zoneId())
        );
        branch.replaceOpeningIntervals(scheduleValidator.validate(request.weeklySchedule()));
        return BranchResponse.from(branch);
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser currentUser) {
        Branch branch = findBranch(id);
        assertOwnerOrAdmin(branch.getBusiness(), currentUser);
        branchRepository.delete(branch);
    }

    private Branch findBranch(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    private void assertOwnerOrAdmin(Business business, AuthenticatedUser currentUser) {
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Branch can only be managed by the business owner or an admin");
    }

    private String normalizeZoneId(String requestedZoneId) {
        String value = requestedZoneId == null || requestedZoneId.isBlank()
                ? defaultZoneId
                : requestedZoneId.trim();
        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid branch zone id");
        }
    }
}
