package com.turnero.branch;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchScheduleExceptionRepository extends JpaRepository<BranchScheduleException, UUID> {
    Optional<BranchScheduleException> findByBranchIdAndDate(UUID branchId, LocalDate date);

    @EntityGraph(attributePaths = {"branch", "branch.business", "branch.business.owner"})
    List<BranchScheduleException> findByBranchIdOrderByDateAsc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "branch.business", "branch.business.owner"})
    Optional<BranchScheduleException> findByIdAndBranchId(UUID id, UUID branchId);

    boolean existsByBranchIdAndDateAndIdNot(UUID branchId, LocalDate date, UUID id);
}
