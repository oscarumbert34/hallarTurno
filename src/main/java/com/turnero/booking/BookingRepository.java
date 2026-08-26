package com.turnero.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource"
    })
    Optional<Booking> findById(UUID id);

    List<Booking> findByBranchIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID branchId,
            Collection<BookingStatus> statuses,
            Instant endsAfter,
            Instant startsBefore
    );

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    Page<Booking> findByBusinessIdOrderByStartsAtAscIdAsc(UUID businessId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    Page<Booking> findByBusinessIdAndBranchIdOrderByStartsAtAscIdAsc(UUID businessId, UUID branchId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    @Query("""
            select booking
            from Booking booking
            where booking.business.id = :businessId
              and (:branchId is null or booking.branch.id = :branchId)
              and (:resourceId is null or booking.resource.id = :resourceId)
              and (:serviceOfferingId is null or booking.serviceOffering.id = :serviceOfferingId)
            order by booking.startsAt asc, booking.id asc
            """)
    Page<Booking> findByBusinessIdAndOptionalFiltersOrderByStartsAtAscIdAsc(
            @Param("businessId") UUID businessId,
            @Param("branchId") UUID branchId,
            @Param("resourceId") UUID resourceId,
            @Param("serviceOfferingId") UUID serviceOfferingId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    List<Booking> findByBusinessIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            UUID businessId,
            Instant startsAtFrom,
            Instant startsAtTo
    );

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    List<Booking> findByBusinessIdAndBranchIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            UUID businessId,
            UUID branchId,
            Instant startsAtFrom,
            Instant startsAtTo
    );

    @EntityGraph(attributePaths = {
            "business",
            "business.owner",
            "customer",
            "branch",
            "serviceOffering",
            "resource",
            "cancelledBy"
    })
    @Query("""
            select booking
            from Booking booking
            where booking.business.id = :businessId
              and booking.startsAt >= :startsAtFrom
              and booking.startsAt < :startsAtTo
              and (:branchId is null or booking.branch.id = :branchId)
              and (:resourceId is null or booking.resource.id = :resourceId)
              and (:serviceOfferingId is null or booking.serviceOffering.id = :serviceOfferingId)
            order by booking.startsAt asc, booking.id asc
            """)
    List<Booking> findByBusinessIdAndDateRangeAndOptionalFiltersOrderByStartsAtAscIdAsc(
            @Param("businessId") UUID businessId,
            @Param("startsAtFrom") Instant startsAtFrom,
            @Param("startsAtTo") Instant startsAtTo,
            @Param("branchId") UUID branchId,
            @Param("resourceId") UUID resourceId,
            @Param("serviceOfferingId") UUID serviceOfferingId
    );
}
