package com.turnero.booking;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.availability.AvailabilityService;
import com.turnero.availability.AvailabilitySlotResponse;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchRepository;
import com.turnero.business.Business;
import com.turnero.business.BusinessConfiguration;
import com.turnero.business.BusinessConfigurationRepository;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.customer.CustomerContact;
import com.turnero.customer.CustomerContactService;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_WEEK_COPY_BOOKINGS = 200;
    private static final String SORT_ORDER = "startsAt:asc,id:asc";
    private static final Set<BookingStatus> COPYABLE_BOOKING_STATUSES = Set.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED
    );

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessConfigurationRepository businessConfigurationRepository;
    private final BranchRepository branchRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final BookableResourceRepository resourceRepository;
    private final AvailabilityService availabilityService;
    private final OwnershipGuard ownershipGuard;
    private final CustomerContactService customerContactService;
    private final BookingConfirmationEmailService bookingConfirmationEmailService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BusinessConfigurationRepository businessConfigurationRepository,
            BranchRepository branchRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            BookableResourceRepository resourceRepository,
            AvailabilityService availabilityService,
            OwnershipGuard ownershipGuard,
            CustomerContactService customerContactService,
            BookingConfirmationEmailService bookingConfirmationEmailService,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.businessConfigurationRepository = businessConfigurationRepository;
        this.branchRepository = branchRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.resourceRepository = resourceRepository;
        this.availabilityService = availabilityService;
        this.ownershipGuard = ownershipGuard;
        this.customerContactService = customerContactService;
        this.bookingConfirmationEmailService = bookingConfirmationEmailService;
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
        CustomerContact customerContact = request.shouldSkipCustomerContact()
                ? null
                : customerContactService.findOrCreateForBooking(
                        branch.getBusiness(),
                        request.customerName(),
                        request.customerPhone(),
                        request.customerEmail()
                );
        String customerEmailSnapshot = resolveCustomerEmailSnapshot(request, customerContact);

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
                customerEmailSnapshot,
                customerContact,
                serviceOffering.getDurationMinutes(),
                serviceOffering.getPrice(),
                serviceOffering.getCurrency(),
                BookingStatus.CONFIRMED
        );
        try {
            Booking savedBooking = bookingRepository.saveAndFlush(booking);
            BookingResponse response = BookingResponse.from(savedBooking);
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
            sendConfirmationEmailAfterCommit(savedBooking);
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

    private void sendConfirmationEmailAfterCommit(Booking savedBooking) {
        if (savedBooking.getCustomerEmailSnapshot() == null || savedBooking.getCustomerEmailSnapshot().isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bookingConfirmationEmailService.sendConfirmation(savedBooking);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bookingConfirmationEmailService.sendConfirmation(savedBooking);
            }
        });
    }

    private String resolveCustomerEmailSnapshot(BookingRequest request, CustomerContact customerContact) {
        if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
            return request.customerEmail().trim().toLowerCase(Locale.ROOT);
        }
        return customerContact == null ? null : customerContact.getEmail();
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

    @Transactional
    public WeeklyBookingCopyResponse copyWeek(
            UUID businessId,
            AuthenticatedUser currentUser,
            WeeklyBookingCopyRequest request
    ) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Bookings can only be copied by the business owner or an admin");
        BusinessConfiguration configuration = businessConfigurationRepository.findById(businessId)
                .orElseGet(() -> businessConfigurationRepository.save(BusinessConfiguration.createDefault(business)));
        if (!configuration.isWeeklyBookingCopyEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Weekly booking copy is not enabled for this business");
        }
        if (request.sourceWeekStart().equals(request.targetWeekStart())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetWeekStart must be different from sourceWeekStart");
        }
        if (request.sourceWeekStart().getDayOfWeek() != request.targetWeekStart().getDayOfWeek()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "sourceWeekStart and targetWeekStart must be the same day of week");
        }

        Branch branchFilter = findBranchFilter(businessId, request.branchId());
        BookableResource resourceFilter = findResourceFilter(businessId, request.resourceId());
        ServiceOffering serviceOfferingFilter = findServiceOfferingFilter(businessId, request.serviceOfferingId());
        OptionalDateRange sourceWeek = new OptionalDateRange(
                request.sourceWeekStart(),
                request.sourceWeekStart().plusDays(6)
        );
        List<Booking> sourceBookings = findBookingEntitiesByLocalDateRange(
                businessId,
                branchFilter,
                resourceFilter,
                serviceOfferingFilter,
                sourceWeek
        ).stream()
                .filter(booking -> COPYABLE_BOOKING_STATUSES.contains(booking.getStatus()))
                .toList();
        if (sourceBookings.size() > MAX_WEEK_COPY_BOOKINGS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Weekly copy supports at most 200 source bookings");
        }

        List<WeeklyBookingCopyCreated> created = new ArrayList<>();
        List<WeeklyBookingCopySkipped> skipped = new ArrayList<>();
        List<WeeklyBookingCopyConflict> conflicts = new ArrayList<>();
        for (Booking source : sourceBookings) {
            copyBookingToTargetWeek(
                    source,
                    request.sourceWeekStart(),
                    request.targetWeekStart(),
                    created,
                    skipped,
                    conflicts
            );
        }

        return new WeeklyBookingCopyResponse(
                request.sourceWeekStart(),
                request.targetWeekStart(),
                sourceBookings.size(),
                created.size(),
                skipped.size(),
                conflicts.size(),
                created,
                skipped,
                conflicts
        );
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
        return findByBusiness(businessId, currentUser, page, size, date, null, null, branchId, null, null);
    }

    @Transactional(readOnly = true)
    public BookingPageResponse findByBusiness(
            UUID businessId,
            AuthenticatedUser currentUser,
            int page,
            int size,
            LocalDate date,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID branchId,
            UUID resourceId,
            UUID serviceOfferingId
    ) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(business, currentUser, "Bookings can only be viewed by the business owner or an admin");
        Branch branchFilter = findBranchFilter(businessId, branchId);
        BookableResource resourceFilter = findResourceFilter(businessId, resourceId);
        ServiceOffering serviceOfferingFilter = findServiceOfferingFilter(businessId, serviceOfferingId);
        OptionalDateRange dateRange = normalizeDateRange(date, dateFrom, dateTo);
        if (dateRange != null) {
            return findByBusinessAndDateRange(
                    businessId,
                    branchFilter,
                    resourceFilter,
                    serviceOfferingFilter,
                    page,
                    size,
                    dateRange
            );
        }
        Page<Booking> bookingPage = bookingRepository.findByBusinessIdAndOptionalFiltersOrderByStartsAtAscIdAsc(
                businessId,
                branchFilter == null ? null : branchFilter.getId(),
                resourceFilter == null ? null : resourceFilter.getId(),
                serviceOfferingFilter == null ? null : serviceOfferingFilter.getId(),
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

    private BookableResource findResourceFilter(UUID businessId, UUID resourceId) {
        if (resourceId == null) {
            return null;
        }
        BookableResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bookable resource not found"));
        if (!resource.getBranch().getBusiness().getId().equals(businessId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bookable resource does not belong to the business");
        }
        return resource;
    }

    private ServiceOffering findServiceOfferingFilter(UUID businessId, UUID serviceOfferingId) {
        if (serviceOfferingId == null) {
            return null;
        }
        ServiceOffering serviceOffering = serviceOfferingRepository.findById(serviceOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
        if (!serviceOffering.getBusiness().getId().equals(businessId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Service offering does not belong to the business");
        }
        return serviceOffering;
    }

    private OptionalDateRange normalizeDateRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (date != null) {
            return new OptionalDateRange(date, date);
        }
        if (dateFrom == null && dateTo == null) {
            return null;
        }
        if (dateFrom == null || dateTo == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Both dateFrom and dateTo are required for range filtering");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "dateFrom must be before or equal to dateTo");
        }
        return new OptionalDateRange(dateFrom, dateTo);
    }

    private BookingPageResponse findByBusinessAndDateRange(
            UUID businessId,
            Branch branchFilter,
            BookableResource resourceFilter,
            ServiceOffering serviceOfferingFilter,
            int page,
            int size,
            OptionalDateRange dateRange
    ) {
        List<Booking> candidates = findBookingEntitiesByLocalDateRange(
                businessId,
                branchFilter,
                resourceFilter,
                serviceOfferingFilter,
                dateRange
        );
        List<BookingResponse> results = candidates.stream()
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

    private List<Booking> findBookingEntitiesByLocalDateRange(
            UUID businessId,
            Branch branchFilter,
            BookableResource resourceFilter,
            ServiceOffering serviceOfferingFilter,
            OptionalDateRange dateRange
    ) {
        List<Branch> branches = branchFilter == null
                ? branchRepository.findDistinctByBusinessIdOrderByNameAsc(businessId)
                : List.of(branchFilter);
        if (branches.isEmpty()) {
            return List.of();
        }
        Instant startsAtFrom = branches.stream()
                .map(branch -> dayStart(dateRange.from(), ZoneId.of(branch.getZoneId())))
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant startsAtTo = branches.stream()
                .map(branch -> dayEnd(dateRange.to(), ZoneId.of(branch.getZoneId())))
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<Booking> candidates = bookingRepository.findByBusinessIdAndDateRangeAndOptionalFiltersOrderByStartsAtAscIdAsc(
                businessId,
                startsAtFrom,
                startsAtTo,
                branchFilter == null ? null : branchFilter.getId(),
                resourceFilter == null ? null : resourceFilter.getId(),
                serviceOfferingFilter == null ? null : serviceOfferingFilter.getId()
        );
        return candidates.stream()
                .filter(booking -> startsInsideLocalDateRange(booking, dateRange))
                .toList();
    }

    private boolean startsInsideLocalDateRange(Booking booking, OptionalDateRange dateRange) {
        LocalDate startsOn = startsOnLocalDate(booking);
        return !startsOn.isBefore(dateRange.from()) && !startsOn.isAfter(dateRange.to());
    }

    private boolean startsOnLocalDate(Booking booking, LocalDate date) {
        return startsOnLocalDate(booking).equals(date);
    }

    private LocalDate startsOnLocalDate(Booking booking) {
        ZoneId branchZoneId = ZoneId.of(booking.getBranch().getZoneId());
        return LocalDateTime.ofInstant(booking.getStartsAt(), branchZoneId).toLocalDate();
    }

    private void copyBookingToTargetWeek(
            Booking source,
            LocalDate sourceWeekStart,
            LocalDate targetWeekStart,
            List<WeeklyBookingCopyCreated> created,
            List<WeeklyBookingCopySkipped> skipped,
            List<WeeklyBookingCopyConflict> conflicts
    ) {
        ZoneId zoneId = ZoneId.of(source.getBranch().getZoneId());
        LocalDate sourceDate = startsOnLocalDate(source);
        LocalTime sourceTime = LocalDateTime.ofInstant(source.getStartsAt(), zoneId).toLocalTime();
        long daysFromSourceWeekStart = ChronoUnit.DAYS.between(sourceWeekStart, sourceDate);
        LocalDate targetDate = targetWeekStart.plusDays(daysFromSourceWeekStart);
        Instant targetStartsAt = LocalDateTime.of(targetDate, sourceTime).atZone(zoneId).toInstant();
        Instant targetEndsAt = LocalDateTime.of(targetDate, sourceTime)
                .plus(Duration.ofMinutes(source.getDurationMinutesSnapshot()))
                .atZone(zoneId)
                .toInstant();

        if (hasEquivalentActiveBooking(source, targetStartsAt)) {
            skipped.add(toSkipped(source, targetDate, sourceTime, "Equivalent booking already exists"));
            return;
        }
        if (!isSlotAvailable(source, targetDate, sourceTime)) {
            conflicts.add(toConflict(source, targetDate, sourceTime, "Slot is not available"));
            return;
        }

        Booking copied = Booking.create(
                source.getBranch(),
                source.getBusiness(),
                source.getCustomer(),
                source.getServiceOffering(),
                source.getResource(),
                targetStartsAt,
                targetEndsAt,
                source.getServiceNameSnapshot(),
                source.getResourceNameSnapshot(),
                source.getCustomerNameSnapshot(),
                source.getCustomerPhoneSnapshot(),
                source.getCustomerEmailSnapshot(),
                source.getCustomerContact(),
                source.getDurationMinutesSnapshot(),
                source.getPriceSnapshot(),
                source.getCurrencySnapshot(),
                source.getStatus()
        );
        Booking saved = bookingRepository.save(copied);
        created.add(new WeeklyBookingCopyCreated(
                source.getId(),
                saved.getId(),
                targetDate,
                sourceTime,
                source.getBranch().getId(),
                source.getResource().getId(),
                source.getServiceOffering().getId()
        ));
    }

    private boolean hasEquivalentActiveBooking(Booking source, Instant targetStartsAt) {
        return bookingRepository.existsByBusinessIdAndBranchIdAndResourceIdAndServiceOfferingIdAndStartsAtAndStatusIn(
                source.getBusiness().getId(),
                source.getBranch().getId(),
                source.getResource().getId(),
                source.getServiceOffering().getId(),
                targetStartsAt,
                COPYABLE_BOOKING_STATUSES
        );
    }

    private boolean isSlotAvailable(Booking source, LocalDate targetDate, LocalTime sourceTime) {
        return availabilityService.findAvailableSlots(
                        source.getBranch().getId(),
                        source.getServiceOffering().getId(),
                        targetDate,
                        source.getResource().getId()
                ).stream()
                .anyMatch(slot -> slot.startsAt().equals(sourceTime)
                        && slot.resourceId().equals(source.getResource().getId()));
    }

    private WeeklyBookingCopySkipped toSkipped(
            Booking source,
            LocalDate targetDate,
            LocalTime startsAt,
            String reason
    ) {
        return new WeeklyBookingCopySkipped(
                source.getId(),
                targetDate,
                startsAt,
                source.getBranch().getId(),
                source.getResource().getId(),
                source.getServiceOffering().getId(),
                reason
        );
    }

    private WeeklyBookingCopyConflict toConflict(
            Booking source,
            LocalDate targetDate,
            LocalTime startsAt,
            String reason
    ) {
        return new WeeklyBookingCopyConflict(
                source.getId(),
                targetDate,
                startsAt,
                source.getBranch().getId(),
                source.getResource().getId(),
                source.getServiceOffering().getId(),
                reason
        );
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

    private record OptionalDateRange(LocalDate from, LocalDate to) {
    }
}


