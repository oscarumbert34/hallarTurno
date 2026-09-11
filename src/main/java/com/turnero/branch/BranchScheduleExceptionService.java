package com.turnero.branch;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchScheduleExceptionService {
    private final BranchRepository branchRepository;
    private final BranchScheduleExceptionRepository repository;
    private final OwnershipGuard ownershipGuard;

    public BranchScheduleExceptionService(BranchRepository branchRepository,
                                          BranchScheduleExceptionRepository repository,
                                          OwnershipGuard ownershipGuard) {
        this.branchRepository = branchRepository;
        this.repository = repository;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public BranchScheduleExceptionResponse create(UUID branchId, BranchScheduleExceptionRequest request,
                                                  AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        requireOwner(branch, currentUser);
        validate(request);
        if (repository.findByBranchIdAndDate(branchId, request.date()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch already has a schedule exception for this date");
        }
        try {
            return BranchScheduleExceptionResponse.from(repository.saveAndFlush(BranchScheduleException.create(
                    branch, request.date(), request.type(), request.startTime(), request.endTime(), request.reason())));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch already has a schedule exception for this date");
        }
    }

    @Transactional(readOnly = true)
    public List<BranchScheduleExceptionResponse> findAll(UUID branchId, AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        requireOwner(branch, currentUser);
        return repository.findByBranchIdOrderByDateAsc(branchId).stream()
                .map(BranchScheduleExceptionResponse::from).toList();
    }

    @Transactional
    public BranchScheduleExceptionResponse update(UUID branchId, UUID id, BranchScheduleExceptionRequest request,
                                                  AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        requireOwner(branch, currentUser);
        BranchScheduleException value = repository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch schedule exception not found"));
        validate(request);
        if (repository.existsByBranchIdAndDateAndIdNot(branchId, request.date(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch already has a schedule exception for this date");
        }
        value.update(request.date(), request.type(), request.startTime(), request.endTime(), request.reason());
        try {
            return BranchScheduleExceptionResponse.from(repository.saveAndFlush(value));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch already has a schedule exception for this date");
        }
    }

    @Transactional
    public void delete(UUID branchId, UUID id, AuthenticatedUser currentUser) {
        Branch branch = findBranch(branchId);
        requireOwner(branch, currentUser);
        BranchScheduleException value = repository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch schedule exception not found"));
        repository.delete(value);
    }

    private void validate(BranchScheduleExceptionRequest request) {
        if (request.type() == BranchScheduleExceptionType.CLOSED) {
            if (request.startTime() != null || request.endTime() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CLOSED exception cannot define start or end time");
            }
            return;
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_HOURS exception requires start and end time");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Schedule exception start must be before end");
        }
    }

    private Branch findBranch(UUID id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    private void requireOwner(Branch branch, AuthenticatedUser currentUser) {
        ownershipGuard.requireOwnerOrAdmin(branch.getBusiness(), currentUser,
                "Branch schedule exceptions can only be managed by the business owner or an admin");
    }
}
