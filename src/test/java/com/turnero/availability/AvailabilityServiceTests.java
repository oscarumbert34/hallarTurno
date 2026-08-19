package com.turnero.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.turnero.booking.Booking;
import com.turnero.booking.BookingRepository;
import com.turnero.branch.Branch;
import com.turnero.branch.BranchOpeningInterval;
import com.turnero.branch.BranchRepository;
import com.turnero.branch.BranchStatus;
import com.turnero.business.Business;
import com.turnero.employee.BookableResource;
import com.turnero.employee.BookableResourceRepository;
import com.turnero.employee.BookableResourceStatus;
import com.turnero.employee.ResourceAbsence;
import com.turnero.employee.ResourceWorkingInterval;
import com.turnero.service.ServiceOffering;
import com.turnero.service.ServiceOfferingRepository;
import com.turnero.service.ServiceOfferingStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final ZoneId ZONE_ID = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private BookableResourceRepository resourceRepository;

    @Mock
    private BookingRepository bookingRepository;

    private AvailabilityService availabilityService;
    private UUID branchId;
    private UUID businessId;
    private UUID serviceId;
    private Branch branch;
    private ServiceOffering serviceOffering;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(
                branchRepository,
                serviceOfferingRepository,
                resourceRepository,
                bookingRepository,
                15
        );
        branchId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
    }

    @Test
    void closedDayReturnsEmptySlots() {
        arrangeBranchAndService(30, List.of(), null);
        BookableResource resource = resource("Ana", List.of(work(DayOfWeek.MONDAY, "09:00", "12:00")), List.of());
        when(resourceRepository.findDistinctByBranchIdOrderByVisibleNameAsc(branchId)).thenReturn(List.of(resource));
        when(bookingRepository.findByBranchIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                org.mockito.ArgumentMatchers.eq(branchId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        List<AvailabilitySlotResponse> slots = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY);

        assertThat(slots).isEmpty();
    }

    @Test
    void partialAbsenceRemovesOnlyOverlappingSlots() {
        arrangeBranchAndService(30, List.of(open(DayOfWeek.MONDAY, "09:00", "12:00")), null);
        BookableResource resource = resource(
                "Ana",
                List.of(work(DayOfWeek.MONDAY, "09:00", "12:00")),
                List.of(absence(MONDAY, "10:00", "10:30"))
        );
        arrangeResources(List.of(resource), List.of());

        List<LocalTime> starts = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY).stream()
                .map(AvailabilitySlotResponse::startsAt)
                .toList();

        assertThat(starts).contains(
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                LocalTime.of(10, 30)
        );
        assertThat(starts).doesNotContain(
                LocalTime.of(10, 0),
                LocalTime.of(9, 15),
                LocalTime.of(9, 45),
                LocalTime.of(10, 15)
        );
    }

    @Test
    void existingBookingPreventsOverlapsAndAllowsContiguousSlots() {
        arrangeBranchAndService(30, List.of(open(DayOfWeek.MONDAY, "09:00", "12:00")), null);
        BookableResource resource = resource("Ana", List.of(work(DayOfWeek.MONDAY, "09:00", "12:00")), List.of());
        Booking booking = booking(resource, "10:00", "10:30");
        arrangeResources(List.of(resource), List.of(booking));

        List<LocalTime> starts = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY).stream()
                .map(AvailabilitySlotResponse::startsAt)
                .toList();

        assertThat(starts).contains(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 30));
        assertThat(starts).doesNotContain(LocalTime.of(9, 45), LocalTime.of(10, 0), LocalTime.of(10, 15));
    }

    @Test
    void serviceDurationsProduceExpectedSlotsAtClosingBoundary() {
        assertStartsForDuration(30, LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertStartsForDuration(45, LocalTime.of(9, 0));
        assertStartsForDuration(60, LocalTime.of(9, 0));
    }

    @Test
    void multipleResourcesCanOfferTheSameSlot() {
        arrangeBranchAndService(30, List.of(open(DayOfWeek.MONDAY, "09:00", "09:30")), null);
        BookableResource first = resource("Ana", List.of(work(DayOfWeek.MONDAY, "09:00", "09:30")), List.of());
        BookableResource second = resource("Box 1", List.of(work(DayOfWeek.MONDAY, "09:00", "09:30")), List.of());
        arrangeResources(List.of(first, second), List.of());

        List<AvailabilitySlotResponse> slots = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY);

        assertThat(slots).hasSize(2);
        assertThat(slots).extracting(AvailabilitySlotResponse::startsAt)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 0));
        assertThat(slots).extracting(AvailabilitySlotResponse::resourceName)
                .containsExactly("Ana", "Box 1");
    }

    @Test
    void dayWithoutResourcesReturnsEmptySlots() {
        arrangeBranchAndService(30, List.of(open(DayOfWeek.MONDAY, "09:00", "12:00")), null);
        arrangeResources(List.of(), List.of());

        List<AvailabilitySlotResponse> slots = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY);

        assertThat(slots).isEmpty();
    }

    @Test
    void resourceFilterLimitsTheResultToTheRequestedResource() {
        arrangeBranchAndService(30, List.of(open(DayOfWeek.MONDAY, "09:00", "09:30")), null);
        BookableResource first = resource("Ana", List.of(work(DayOfWeek.MONDAY, "09:00", "09:30")), List.of());
        BookableResource second = resource("Beto", List.of(work(DayOfWeek.MONDAY, "09:00", "09:30")), List.of());
        arrangeResources(List.of(first, second), List.of());
        when(resourceRepository.findById(first.getId())).thenReturn(Optional.of(first));

        List<AvailabilitySlotResponse> slots = availabilityService.findAvailableSlots(
                branchId,
                serviceId,
                MONDAY,
                first.getId()
        );

        assertThat(slots).singleElement()
                .extracting(AvailabilitySlotResponse::resourceId)
                .isEqualTo(first.getId());
    }

    private void assertStartsForDuration(int duration, LocalTime... expectedStarts) {
        arrangeBranchAndService(duration, List.of(open(DayOfWeek.MONDAY, "09:00", "10:00")), null);
        BookableResource resource = resource("Ana", List.of(work(DayOfWeek.MONDAY, "09:00", "10:00")), List.of());
        arrangeResources(List.of(resource), List.of());

        List<LocalTime> starts = availabilityService.findAvailableSlots(branchId, serviceId, MONDAY).stream()
                .map(AvailabilitySlotResponse::startsAt)
                .toList();

        assertThat(starts).containsExactly(expectedStarts);
    }

    private void arrangeBranchAndService(
            int durationMinutes,
            List<BranchOpeningInterval> openingIntervals,
            Branch serviceBranch
    ) {
        Business business = mock(Business.class);
        when(business.getId()).thenReturn(businessId);

        branch = mock(Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.getBusiness()).thenReturn(business);
        when(branch.getStatus()).thenReturn(BranchStatus.ACTIVE);
        when(branch.getZoneId()).thenReturn(ZONE_ID.getId());
        when(branch.getOpeningIntervals()).thenReturn(openingIntervals);

        serviceOffering = mock(ServiceOffering.class);
        when(serviceOffering.getId()).thenReturn(serviceId);
        when(serviceOffering.getBusiness()).thenReturn(business);
        when(serviceOffering.getBranch()).thenReturn(serviceBranch);
        when(serviceOffering.getDurationMinutes()).thenReturn(durationMinutes);
        when(serviceOffering.getStatus()).thenReturn(ServiceOfferingStatus.ACTIVE);

        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(serviceOfferingRepository.findById(serviceId)).thenReturn(Optional.of(serviceOffering));
    }

    private void arrangeResources(List<BookableResource> resources, List<Booking> bookings) {
        when(resourceRepository.findDistinctByBranchIdOrderByVisibleNameAsc(branchId)).thenReturn(resources);
        when(bookingRepository.findByBranchIdAndStatusInAndStartsAtLessThanAndEndsAtGreaterThan(
                org.mockito.ArgumentMatchers.eq(branchId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(bookings);
    }

    private BookableResource resource(
            String name,
            List<ResourceWorkingInterval> workingIntervals,
            List<ResourceAbsence> absences
    ) {
        BookableResource resource = mock(BookableResource.class);
        when(resource.getId()).thenReturn(UUID.randomUUID());
        when(resource.getBranch()).thenReturn(branch);
        when(resource.getVisibleName()).thenReturn(name);
        when(resource.getStatus()).thenReturn(BookableResourceStatus.ACTIVE);
        when(resource.getServiceOfferings()).thenReturn(Set.of(serviceOffering));
        when(resource.getWorkingIntervals()).thenReturn(workingIntervals);
        when(resource.getAbsences()).thenReturn(absences);
        return resource;
    }

    private BranchOpeningInterval open(DayOfWeek dayOfWeek, String opensAt, String closesAt) {
        BranchOpeningInterval interval = mock(BranchOpeningInterval.class);
        when(interval.getDayOfWeek()).thenReturn(dayOfWeek);
        when(interval.getOpensAt()).thenReturn(LocalTime.parse(opensAt));
        when(interval.getClosesAt()).thenReturn(LocalTime.parse(closesAt));
        return interval;
    }

    private ResourceWorkingInterval work(DayOfWeek dayOfWeek, String startsAt, String endsAt) {
        ResourceWorkingInterval interval = mock(ResourceWorkingInterval.class);
        when(interval.getDayOfWeek()).thenReturn(dayOfWeek);
        when(interval.getStartsAt()).thenReturn(LocalTime.parse(startsAt));
        when(interval.getEndsAt()).thenReturn(LocalTime.parse(endsAt));
        return interval;
    }

    private ResourceAbsence absence(LocalDate date, String startsAt, String endsAt) {
        ResourceAbsence absence = mock(ResourceAbsence.class);
        when(absence.getDate()).thenReturn(date);
        when(absence.getStartsAt()).thenReturn(LocalTime.parse(startsAt));
        when(absence.getEndsAt()).thenReturn(LocalTime.parse(endsAt));
        return absence;
    }

    private Booking booking(BookableResource resource, String startsAt, String endsAt) {
        Booking booking = mock(Booking.class);
        when(booking.getResource()).thenReturn(resource);
        when(booking.getStartsAt()).thenReturn(toInstant(startsAt));
        when(booking.getEndsAt()).thenReturn(toInstant(endsAt));
        return booking;
    }

    private Instant toInstant(String time) {
        return LocalDateTime.of(MONDAY, LocalTime.parse(time)).atZone(ZONE_ID).toInstant();
    }
}
