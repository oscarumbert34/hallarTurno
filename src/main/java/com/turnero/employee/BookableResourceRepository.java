package com.turnero.employee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookableResourceRepository extends JpaRepository<BookableResource, UUID> {

    @EntityGraph(attributePaths = {
            "branch",
            "branch.business",
            "branch.business.owner",
            "serviceOfferings",
            "workingIntervals",
            "absences"
    })
    Optional<BookableResource> findById(UUID id);

    @EntityGraph(attributePaths = {"branch", "branch.business", "serviceOfferings", "workingIntervals", "absences"})
    List<BookableResource> findDistinctByBranchIdOrderByVisibleNameAsc(UUID branchId);
}
