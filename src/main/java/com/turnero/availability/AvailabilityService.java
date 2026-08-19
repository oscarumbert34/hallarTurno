package com.turnero.availability;

import com.turnero.booking.Booking;
import com.turnero.booking.BookingRepository;
import com.turnero.booking.BookingStatus;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchOpeningInterval;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.common.ApiException;
import com.turnero.employee.BookableResource;
import com.turnero.employee.BookableResourceRepository;
import com.turnero.employee.BookableResourceStatus;
import com.turnero.employee.ResourceAbsence;
import com.turnero.employee.ResourceWorkingInterval;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private static final Set<BookingStatus> ACTIVE_BOOKING_STATUSES = Set.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED
    );

    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookableResourceRepository resourceRepository;
    private final BookingRepository bookingRepository;
    private final Duration slotGranularity;

    public AvailabilityService(
            BranchRepository branchRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            BookableResourceRepository resourceRepository,
            BookingRepository bookingRepository,
            @Value("${availability.slot-granularity-minutes:15}") long slotGranularityMinutes
    ) {
        this.branchRepository = branchRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.resourceRepository = resourceRepository;
        this.bookingRepository = bookingRepository;
        this.slotGranularity = Duration.ofMinutes(slotGranularityMinutes);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> findAvailableSlots(
            UUID branchId,
            UUID serviceOfferingId,
            LocalDate date,
            UUID resourceId
    ) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Availability date is required");
        }
        if (slotGranularity.isZero() || slotGranularity.isNegative()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid availability slot granularity");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        ServiceOffering serviceOffering = serviceOfferingRepository.findById(serviceOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
        assertServiceBelongsToBranch(serviceOffering, branch);

        if (branch.getStatus() != BranchStatus.ACTIVE || serviceOffering.getStatus() != ServiceOfferingStatus.ACTIVE) {
            return List.of();
        }

        ZoneId zoneId = ZoneId.of(branch.getZoneId());
        List<Booking> bookings = bookingRepository.findByBranchIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                branch.getId(),
                ACTIVE_BOOKING_STATUSES,
                dayEnd(date, zoneId),
                dayStart(date, zoneId)
        );
        List<BookableResource> resources = findCandidateResources(branch, serviceOffering, resourceId);

        return resources.stream()
                .filter(resource -> canOfferService(resource, serviceOffering))
                .flatMap(resource -> calculateSlots(branch, serviceOffering, resource, date, zoneId, bookings).stream())
                .sorted(Comparator.comparing(AvailabilitySlotResponse::startsAt)
                        .thenComparing(AvailabilitySlotResponse::resourceName)
                        .thenComparing(AvailabilitySlotResponse::resourceId))
                .toList();
    }

    public List<AvailabilitySlotResponse> findAvailableSlots(
            UUID branchId,
            UUID serviceOfferingId,
            LocalDate date
    ) {
        return findAvailableSlots(branchId, serviceOfferingId, date, null);
    }

    private List<BookableResource> findCandidateResources(
            Branch branch,
            ServiceOffering serviceOffering,
            UUID resourceId
    ) {
        if (resourceId == null) {
            return resourceRepository.findDistinctByBranchIdOrderByVisibleNameAsc(branch.getId()).stream()
                    .filter(resource -> resource.getStatus() == BookableResourceStatus.ACTIVE)
                    .toList();
        }

        BookableResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bookable resource not found"));
        if (!resource.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bookable resource does not belong to the branch");
        }
        if (resource.getStatus() != BookableResourceStatus.ACTIVE || !canOfferService(resource, serviceOffering)) {
            return List.of();
        }
        return List.of(resource);
    }

    private List<AvailabilitySlotResponse> calculateSlots(
            Branch branch,
            ServiceOffering serviceOffering,
            BookableResource resource,
            LocalDate date,
            ZoneId zoneId,
            List<Booking> bookings
    ) {
        List<TimeRange> openingRanges = intervalsForDay(branch.getOpeningIntervals(), date);
        List<TimeRange> workingRanges = workingIntervalsForDay(resource.getWorkingIntervals(), date);
        if (openingRanges.isEmpty() || workingRanges.isEmpty()) {
            return List.of();
        }

        Duration serviceDuration = Duration.ofMinutes(serviceOffering.getDurationMinutes());
        List<TimeRange> blockedRanges = blockedRanges(resource, bookings, date, zoneId);

        return openingRanges.stream()
                .flatMap(opening -> workingRanges.stream().map(opening::intersection))
                .flatMap(Optional::stream)
                .flatMap(availableRange -> slotsWithin(availableRange, serviceDuration, blockedRanges).stream())
                .map(slot -> new AvailabilitySlotResponse(
                        slot.startsAt().toLocalTime(),
                        slot.endsAt().toLocalTime(),
                        resource.getId(),
                        resource.getVisibleName()
                ))
                .toList();
    }

    private List<TimeRange> slotsWithin(
            TimeRange availableRange,
            Duration serviceDuration,
            List<TimeRange> blockedRanges
    ) {
        LocalDateTime cursor = availableRange.startsAt();
        LocalDateTime latestEnd = availableRange.endsAt();
        List<TimeRange> slots = new java.util.ArrayList<>();

        while (!cursor.plus(serviceDuration).isAfter(latestEnd)) {
            TimeRange slot = new TimeRange(cursor, cursor.plus(serviceDuration));
            if (blockedRanges.stream().noneMatch(slot::overlaps)) {
                slots.add(slot);
            }
            cursor = cursor.plus(slotStep(serviceDuration));
        }
        return slots;
    }

    private Duration slotStep(Duration serviceDuration) {
        return serviceDuration.compareTo(slotGranularity) > 0 ? serviceDuration : slotGranularity;
    }

    private List<TimeRange> blockedRanges(
            BookableResource resource,
            List<Booking> bookings,
            LocalDate date,
            ZoneId zoneId
    ) {
        List<TimeRange> absences = resource.getAbsences().stream()
                .filter(absence -> absence.getDate().equals(date))
                .map(absence -> new TimeRange(
                        LocalDateTime.of(date, absence.getStartsAt()),
                        LocalDateTime.of(date, absence.getEndsAt())
                ))
                .toList();
        List<TimeRange> booked = bookings.stream()
                .filter(booking -> booking.getResource().getId().equals(resource.getId()))
                .map(booking -> new TimeRange(
                        LocalDateTime.ofInstant(booking.getStartsAt(), zoneId),
                        LocalDateTime.ofInstant(booking.getEndsAt(), zoneId)
                ))
                .toList();

        List<TimeRange> blocked = new java.util.ArrayList<>(absences);
        blocked.addAll(booked);
        return blocked;
    }

    private List<TimeRange> intervalsForDay(Collection<BranchOpeningInterval> intervals, LocalDate date) {
        return intervals.stream()
                .filter(interval -> interval.getDayOfWeek() == date.getDayOfWeek())
                .map(interval -> new TimeRange(
                        LocalDateTime.of(date, interval.getOpensAt()),
                        LocalDateTime.of(date, interval.getClosesAt())
                ))
                .toList();
    }

    private List<TimeRange> workingIntervalsForDay(Collection<ResourceWorkingInterval> intervals, LocalDate date) {
        return intervals.stream()
                .filter(interval -> interval.getDayOfWeek() == date.getDayOfWeek())
                .map(interval -> new TimeRange(
                        LocalDateTime.of(date, interval.getStartsAt()),
                        LocalDateTime.of(date, interval.getEndsAt())
                ))
                .toList();
    }

    private boolean canOfferService(BookableResource resource, ServiceOffering serviceOffering) {
        return resource.getServiceOfferings().stream()
                .anyMatch(offering -> offering.getId().equals(serviceOffering.getId()));
    }

    private void assertServiceBelongsToBranch(ServiceOffering serviceOffering, Branch branch) {
        if (!serviceOffering.getBusiness().getId().equals(branch.getBusiness().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service offering does not belong to the branch business");
        }
        if (serviceOffering.getBranch() != null && !serviceOffering.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch-specific service offering does not belong to the branch");
        }
    }

    private Instant dayStart(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Instant dayEnd(LocalDate date, ZoneId zoneId) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private record TimeRange(LocalDateTime startsAt, LocalDateTime endsAt) {

        Optional<TimeRange> intersection(TimeRange other) {
            LocalDateTime start = startsAt.isAfter(other.startsAt) ? startsAt : other.startsAt;
            LocalDateTime end = endsAt.isBefore(other.endsAt) ? endsAt : other.endsAt;
            if (!start.isBefore(end)) {
                return Optional.empty();
            }
            return Optional.of(new TimeRange(start, end));
        }

        boolean overlaps(TimeRange other) {
            return startsAt.isBefore(other.endsAt) && endsAt.isAfter(other.startsAt);
        }
    }
}
