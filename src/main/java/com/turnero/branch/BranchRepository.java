package com.turnero.branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    @EntityGraph(attributePaths = {"business", "business.owner", "openingIntervals"})
    Optional<Branch> findById(UUID id);

    @EntityGraph(attributePaths = {"business", "business.owner", "openingIntervals"})
    List<Branch> findDistinctByBusinessIdOrderByNameAsc(UUID businessId);

    @EntityGraph(attributePaths = {"business", "openingIntervals"})
    @Query("""
            select distinct branch
            from Branch branch
            where branch.business.id in :businessIds
              and branch.status = :status
              and (:hasLocality = false or lower(branch.locality) = :locality)
            order by branch.business.name asc, branch.name asc
            """)
    List<Branch> findPublicActiveBranchesForBusinesses(
            @Param("businessIds") java.util.Collection<UUID> businessIds,
            @Param("status") BranchStatus status,
            @Param("hasLocality") boolean hasLocality,
            @Param("locality") String locality
    );
}
