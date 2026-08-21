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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookableResourceRepository resourceRepository;
    private final AvailabilityService availabilityService;
    private final OwnershipGuard ownershipGuard;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            BookableResourceRepository resourceRepository,
            AvailabilityService availabilityService,
            OwnershipGuard ownershipGuard,
            Clock clock
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
            return BookingResponse.from(bookingRepository.saveAndFlush(booking));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking slot is no longer available");
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
        return findByBusiness(businessId, currentUser, page, size, null);
    }

    @Transactional(readOnly = true)
    public BookingPageResponse findByBusiness(
            UUID businessId,
            AuthenticatedUser currentUser,
            int page,
            int size,
            LocalDate date
    ) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Bookings can only be viewed by the business owner or an admin");
        if (date != null) {
            return findByBusinessAndDate(businessId, page, size, date);
        }
        Page<BookingResponse> bookings = bookingRepository.findByBusinessIdOrderByStartsAtAscIdAsc(
                        businessId,
                        PageRequest.of(page, size)
                )
                .map(BookingResponse::from);
        return new BookingPageResponse(
                bookings.getNumber(),
                bookings.getSize(),
                bookings.getTotalElements(),
                bookings.getTotalPages(),
                bookings.getContent()
        );
    }

    private BookingPageResponse findByBusinessAndDate(UUID businessId, int page, int size, LocalDate date) {
        List<Branch> branches = branchRepository.findDistinctByBusinessIdOrderByNameAsc(businessId);
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
        List<BookingResponse> results = bookingRepository
                .findByBusinessIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                        businessId,
                        startsAtFrom,
                        startsAtTo
                ).stream()
                .filter(booking -> startsOnLocalDate(booking, date))
                .map(BookingResponse::from)
                .toList();
        int fromIndex = (int) Math.min((long) page * size, results.size());
        int toIndex = Math.min(fromIndex + size, results.size());
        return new BookingPageResponse(
                page,
                size,
                results.size(),
                (int) Math.ceil((double) results.size() / size),
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
        return new BookingPageResponse(page, size, 0, 0, List.of());
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


