package com.turnero.booking;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.availability.AvailabilityService;
import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.employee.BookableResource;
import com.turnero.employee.BookableResourceRepository;
import com.turnero.security.OwnershipGuard;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final String SORT_ORDER = "startsAt:asc,id:asc";

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookableResourceRepository resourceRepository;
    private final AvailabilityService availabilityService;
    private final OwnershipGuard ownershipGuard;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            BookableResourceRepository resourceRepository,
            AvailabilityService availabilityService,
            OwnershipGuard ownershipGuard,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.resourceRepository = resourceRepository;
        this.availabilityService = availabilityService;
        this.ownershipGuard = ownershipGuard;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public BookingResponse create(BookingRequest request, AuthenticatedUser currentUser) {
        User customer = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        return createBooking(request, customer);
    }

    @Transactional
    public BookingResponse createPublic(BookingRequest request) {
        return createBooking(request, null);
    }

    private BookingResponse createBooking(BookingRequest request, User customer) {
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        ServiceOffering serviceOffering = serviceOfferingRepository.findById(request.serviceOfferingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
        BookableResource resource = resourceRepository.findById(request.resourceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bookable resource not found"));

        assertConsistentRequest(branch, serviceOffering, resource);
        assertSlotAvailable(request);

        ZoneId zoneId = ZoneId.of(branch.getZoneId());
        Instant startsAt = LocalDateTime.of(request.date(), request.startsAt()).atZone(zoneId).toInstant();
        Instant endsAt = LocalDateTime.of(request.date(), request.startsAt())
                .plus(Duration.ofMinutes(serviceOffering.getDurationMinutes()))
                .atZone(zoneId)
                .toInstant();

        Booking booking = Booking.create(
                branch,
                branch.getBusiness(),
                customer,
                serviceOffering,
                resource,
                startsAt,
                endsAt,
                serviceOffering.getName(),
                resource.getVisibleName(),
                request.customerName().trim(),
                request.customerPhone().trim(),
                serviceOffering.getDurationMinutes(),
                serviceOffering.getPrice(),
                serviceOffering.getCurrency(),
                BookingStatus.CONFIRMED
        );
        try {
            BookingResponse response = BookingResponse.from(bookingRepository.saveAndFlush(booking));
            meterRegistry.counter("turnero.bookings.created", "channel", customer == null ? "public" : "authenticated").increment();
            log.info(
                    "booking created id={} businessId={} branchId={} serviceOfferingId={} resourceId={} startsAt={}",
                    response.id(),
                    response.businessId(),
                    response.branchId(),
                    response.serviceOfferingId(),
                    response.resourceId(),
                    response.startsAt()
            );
            return response;
        } catch (DataIntegrityViolationException exception) {
            meterRegistry.counter("turnero.bookings.conflicts").increment();
            log.warn(
                    "booking slot conflict businessId={} branchId={} serviceOfferingId={} resourceId={} date={} startsAt={}",
                    branch.getBusiness().getId(),
                    request.branchId(),
                    request.serviceOfferingId(),
                    request.resourceId(),
                    request.date(),
                    request.startsAt()
            );
            throw new ApiException(HttpStatus.CONFLICT, "Booking slot is already taken");
        }
    }

    @Transactional
    public BookingResponse cancel(java.util.UUID id, AuthenticatedUser currentUser) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        User cancelledBy = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        assertCanCancel(booking, currentUser);
        booking.cancel(cancelledBy, Instant.now(clock));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingPageResponse findByBusiness(UUID businessId, AuthenticatedUser currentUser, int page, int size) {
        return findByBusiness(businessId, currentUser, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public BookingPageResponse findByBusiness(
            UUID businessId,
            AuthenticatedUser currentUser,
            int page,
            int size,
            LocalDate date,
            UUID branchId
    ) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Bookings can only be viewed by the business owner or an admin");
        Branch branchFilter = findBranchFilter(businessId, branchId);
        if (date != null) {
            return findByBusinessAndDate(businessId, branchFilter, page, size, date);
        }
        Page<Booking> bookingPage = branchFilter == null
                ? bookingRepository.findByBusinessIdOrderByStartsAtAscIdAsc(
                        businessId,
                        PageRequest.of(page, size)
                )
                : bookingRepository.findByBusinessIdAndBranchIdOrderByStartsAtAscIdAsc(
                        businessId,
                        branchFilter.getId(),
                        PageRequest.of(page, size)
                );
        Page<BookingResponse> bookings = bookingPage
                .map(BookingResponse::from);
        return new BookingPageResponse(
                bookings.getNumber(),
                bookings.getSize(),
                MAX_PAGE_SIZE,
                bookings.getTotalElements(),
                bookings.getTotalPages(),
                bookings.hasNext(),
                SORT_ORDER,
                bookings.getContent()
        );
    }

    private Branch findBranchFilter(UUID businessId, UUID branchId) {
        if (branchId == null) {
            return null;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found"));
        if (!branch.getBusiness().getId().equals(businessId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch does not belong to the business");
        }
        return branch;
    }

    private BookingPageResponse findByBusinessAndDate(
            UUID businessId,
            Branch branchFilter,
            int page,
            int size,
            LocalDate date
    ) {
        List<Branch> branches = branchFilter == null
                ? branchRepository.findDistinctByBusinessIdOrderByNameAsc(businessId)
                : List.of(branchFilter);
        if (branches.isEmpty()) {
            return emptyPage(page, size);
        }
        Instant startsAtFrom = branches.stream()
                .map(branch -> dayStart(date, ZoneId.of(branch.getZoneId())))
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant startsAtTo = branches.stream()
                .map(branch -> dayEnd(date, ZoneId.of(branch.getZoneId())))
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<Booking> candidates = branchFilter == null
                ? bookingRepository.findByBusinessIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                        businessId,
                        startsAtFrom,
                        startsAtTo
                )
                : bookingRepository.findByBusinessIdAndBranchIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                        businessId,
                        branchFilter.getId(),
                        startsAtFrom,
                        startsAtTo
                );
        List<BookingResponse> results = candidates.stream()
                .filter(booking -> startsOnLocalDate(booking, date))
                .map(BookingResponse::from)
                .toList();
        int fromIndex = (int) Math.min((long) page * size, results.size());
        int toIndex = Math.min(fromIndex + size, results.size());
        return new BookingPageResponse(
                page,
                size,
                MAX_PAGE_SIZE,
                results.size(),
                (int) Math.ceil((double) results.size() / size),
                toIndex < results.size(),
                SORT_ORDER,
                results.subList(fromIndex, toIndex)
        );
    }

    private boolean startsOnLocalDate(Booking booking, LocalDate date) {
        ZoneId branchZoneId = ZoneId.of(booking.getBranch().getZoneId());
        return LocalDateTime.ofInstant(booking.getStartsAt(), branchZoneId).toLocalDate().equals(date);
    }

    private Instant dayStart(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Instant dayEnd(LocalDate date, ZoneId zoneId) {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private BookingPageResponse emptyPage(int page, int size) {
        return new BookingPageResponse(page, size, MAX_PAGE_SIZE, 0, 0, false, SORT_ORDER, List.of());
    }

    private void assertSlotAvailable(BookingRequest request) {
        boolean available = availabilityService.findAvailableSlots(
                        request.branchId(),
                        request.serviceOfferingId(),
                        request.date(),
                        request.resourceId()
                ).stream()
                .anyMatch(slot -> slot.startsAt().equals(request.startsAt())
                        && slot.resourceId().equals(request.resourceId()));
        if (!available) {
            meterRegistry.counter("turnero.bookings.conflicts").increment();
            log.warn(
                    "booking unavailable slot branchId={} serviceOfferingId={} resourceId={} date={} startsAt={}",
                    request.branchId(),
                    request.serviceOfferingId(),
                    request.resourceId(),
                    request.date(),
                    request.startsAt()
            );
            throw new ApiException(HttpStatus.CONFLICT, "Booking slot is not available");
        }
    }

    private void assertConsistentRequest(
            Branch branch,
            ServiceOffering serviceOffering,
            BookableResource resource
    ) {
        if (!resource.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bookable resource does not belong to the branch");
        }
        if (!serviceOffering.getBusiness().getId().equals(branch.getBusiness().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service offering does not belong to the branch business");
        }
        if (serviceOffering.getBranch() != null && !serviceOffering.getBranch().getId().equals(branch.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch-specific service offering does not belong to the branch");
        }
        boolean resourceOffersService = resource.getServiceOfferings().stream()
                .anyMatch(offering -> offering.getId().equals(serviceOffering.getId()));
        if (!resourceOffersService) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bookable resource cannot perform the service offering");
        }
    }

    private void assertCanCancel(Booking booking, AuthenticatedUser currentUser) {
        if ((booking.getCustomer() != null && booking.getCustomer().getId().equals(currentUser.id()))
                || booking.getBusiness().getOwner().getId().equals(currentUser.id())
                || currentUser.roles().contains(UserRole.ADMIN)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Booking can only be cancelled by the customer, business owner or an admin");
    }
}


