package com.turnero.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    @EntityGraph(attributePaths = {"business", "business.owner", "branch"})
    Optional<ServiceOffering> findById(UUID id);

    @EntityGraph(attributePaths = {"business", "business.owner", "branch"})
    List<ServiceOffering> findByBusinessIdOrderByNameAsc(UUID businessId);

    @EntityGraph(attributePaths = {"business", "business.owner", "branch"})
    List<ServiceOffering> findByBusinessIdAndStatusOrderByNameAsc(
            UUID businessId,
            ServiceOfferingStatus status
    );

    @EntityGraph(attributePaths = {"business", "branch"})
    @Query("""
            select offering
            from ServiceOffering offering
            join offering.business business
            left join offering.branch branch
            where offering.status = :offeringStatus
              and business.status = :businessStatus
              and (
                    :hasText = false
                    or lower(function('unaccent', offering.name)) like :textPattern
                    or lower(coalesce(offering.description, '')) like :textPattern
                    or lower(business.name) like :textPattern
              )
              and (
                    :hasLocality = false
                    or (branch is not null and lower(branch.locality) = :locality)
                    or (
                        branch is null
                        and exists (
                            select candidateBranch.id
                            from Branch candidateBranch
                            where candidateBranch.business = business
                              and candidateBranch.status = :branchStatus
                              and lower(candidateBranch.locality) = :locality
                        )
                    )
              )
            order by business.name asc, offering.name asc
            """)
    Page<ServiceOffering> searchPublicAvailabilityCandidates(
            @Param("hasText") boolean hasText,
            @Param("textPattern") String textPattern,
            @Param("hasLocality") boolean hasLocality,
            @Param("locality") String locality,
            @Param("businessStatus") com.turnero.business.BusinessStatus businessStatus,
            @Param("branchStatus") com.turnero.branch.BranchStatus branchStatus,
            @Param("offeringStatus") ServiceOfferingStatus offeringStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"business", "branch"})
    @Query("""
            select offering
            from ServiceOffering offering
            join offering.business business
            where business.id in :businessIds
              and offering.status = :offeringStatus
              and (
                    :hasText = false
                    or lower(function('unaccent', offering.name)) like :textPattern
                    or lower(coalesce(offering.description, '')) like :textPattern
                    or lower(business.name) like :textPattern
              )
            order by business.name asc, offering.name asc
            """)
    List<ServiceOffering> findPublicActiveOfferingsForBusinesses(
            @Param("businessIds") java.util.Collection<UUID> businessIds,
            @Param("hasText") boolean hasText,
            @Param("textPattern") String textPattern,
            @Param("offeringStatus") ServiceOfferingStatus offeringStatus
    );
}
