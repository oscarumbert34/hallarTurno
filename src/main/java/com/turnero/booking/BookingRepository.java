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
    List<Booking> findByBusinessIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            UUID businessId,
            Instant startsAtFrom,
            Instant startsAtTo
    );
}
