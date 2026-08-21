package com.turnero.business;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "owner")
    List<Business> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Business> findByStatusOrderByNameAsc(BusinessStatus status);

    @Query("""
            select business
            from Business business
            where business.status = :businessStatus
              and (:hasBusinessId = false or business.id = :businessId)
              and (
                    :hasText = false
                    or lower(business.name) like :textPattern
                    or exists (
                        select offering.id
                        from ServiceOffering offering
                        where offering.business = business
                          and offering.status = :offeringStatus
                          and (
                                lower(function('unaccent', offering.name)) like :textPattern
                                or lower(coalesce(offering.description, '')) like :textPattern
                          )
                    )
              )
              and (
                    :hasLocality = false
                    or exists (
                        select branch.id
                        from Branch branch
                        where branch.business = business
                          and branch.status = :branchStatus
                          and lower(branch.locality) = :locality
                    )
              )
            order by business.name asc
            """)
    Page<Business> searchPublicAvailabilityBusinesses(
            @Param("hasText") boolean hasText,
            @Param("textPattern") String textPattern,
            @Param("hasLocality") boolean hasLocality,
            @Param("locality") String locality,
            @Param("hasBusinessId") boolean hasBusinessId,
            @Param("businessId") UUID businessId,
            @Param("businessStatus") BusinessStatus businessStatus,
            @Param("branchStatus") com.turnero.branch.BranchStatus branchStatus,
            @Param("offeringStatus") com.turnero.service.ServiceOfferingStatus offeringStatus,
            Pageable pageable
    );
}
