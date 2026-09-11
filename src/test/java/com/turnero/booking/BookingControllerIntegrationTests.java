package com.turnero.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.customer.CustomerContactRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero")
            .withUsername("turnero")
            .withPassword("turnero");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CustomerContactRepository customerContactRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void publicBookingCanBeCreatedWithoutAuthentication() throws Exception {
        Fixture fixture = fixture("booking-public");

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicBookingJsonWithEmail(fixture, "09:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.customerName").value("Cliente " + fixture.prefix()))
                .andExpect(jsonPath("$.customerPhone").value("+54 11 5555-1234"))
                .andExpect(jsonPath("$.customerEmail").value("cliente@example.com"))
                .andExpect(jsonPath("$.serviceName").value("Servicio " + fixture.prefix()));
    }

    @Test
    void firstPublicBookingRequiresCustomerEmail() throws Exception {
        Fixture fixture = fixture("booking-public-email-required");

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(fixture, "09:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer email is required for the first booking"));
    }

    @Test
    void publicBookingCanSkipReusableCustomerContact() throws Exception {
        Fixture fixture = fixture("booking-skip-contact");

        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(fixture, "09:00", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerContactId").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.customerName").value("Cliente " + fixture.prefix()))
                .andExpect(jsonPath("$.customerPhone").value("+54 11 5555-1234"));

        assertThat(customerContactRepository.findByBusinessIdAndNormalizedPhone(
                UUID.fromString(fixture.businessId()),
                "541155551234"
        )).isEmpty();
    }

    @Test
    void customerCreatesAndCancelsOwnBookingWithoutDeletingIt() throws Exception {
        Fixture fixture = fixture("booking-own");
        String bookingId = createBooking(fixture.customerToken(), fixture, "09:00");

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + fixture.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").exists());

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)))
                .isPresent()
                .get()
                .extracting(Booking::getStatus)
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void businessOwnerCanCancelBookingAndAnotherUserCannot() throws Exception {
        Fixture fixture = fixture("booking-auth");
        String otherToken = registerAndGetToken("booking-auth-other@example.com", "CUSTOMER");
        String firstBookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        String secondBookingId = createBooking(fixture.customerToken(), fixture, "09:30");

        mockMvc.perform(post("/api/v1/bookings/" + firstBookingId + "/cancel")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/" + secondBookingId + "/cancel")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessOwnerCanRescheduleBookingAndTheCurrentBookingDoesNotBlockItself() throws Exception {
        Fixture fixture = fixture("booking-reschedule");
        String bookingId = createBooking(fixture.customerToken(), fixture, "09:00");

        mockMvc.perform(put("/api/v1/bookings/" + bookingId + "/reschedule")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-09-07",
                                  "startTime": "09:30"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.startsAt").value("2026-09-07T12:30:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2026-09-07T13:00:00Z"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void bookingDepositStatusFollowsBusinessConfiguration() throws Exception {
        Fixture disabled = fixture("booking-deposit-disabled");

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + disabled.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJsonWithDepositPaid(disabled, "09:00", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositStatus").value("NOT_REQUIRED"));

        Fixture enabled = fixture("booking-deposit-enabled");
        enableDeposits(enabled.ownerToken(), enabled.businessId());

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + enabled.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJsonWithDepositPaid(enabled, "09:00", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositStatus").value("PENDING"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + enabled.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJsonWithDepositPaid(enabled, "09:30", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositStatus").value("PAID"));
    }

    @Test
    void ownerCanChangeDepositBetweenPendingAndPaid() throws Exception {
        Fixture fixture = fixture("booking-deposit-change");
        enableDeposits(fixture.ownerToken(), fixture.businessId());
        String bookingId = createBooking(fixture.customerToken(), fixture, "09:00");

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/deposit-status")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositStatus\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositStatus").value("PAID"));

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/deposit-status")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositStatus\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositStatus").value("PENDING"));
    }

    @Test
    void depositUpdateRejectsMissingBookingForeignUserAndDisabledBusiness() throws Exception {
        Fixture fixture = fixture("booking-deposit-rules");
        String bookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        String otherOwnerToken = registerAndGetToken("booking-deposit-other@example.com", "BUSINESS");

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/deposit-status")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositStatus\":\"PAID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Deposits are not enabled for this business"));

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/deposit-status")
                        .header("Authorization", "Bearer " + otherOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositStatus\":\"PAID\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/bookings/" + UUID.randomUUID() + "/deposit-status")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositStatus\":\"PAID\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));
    }

    @Test
    void rescheduleRejectsOccupiedSlotCancelledBookingAndForeignUser() throws Exception {
        Fixture fixture = fixture("booking-reschedule-rules");
        String firstBookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        String secondBookingId = createBooking(fixture.customerToken(), fixture, "09:30");
        String otherOwnerToken = registerAndGetToken("booking-reschedule-foreign@example.com", "BUSINESS");

        mockMvc.perform(put("/api/v1/bookings/" + firstBookingId + "/reschedule")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-07\",\"startTime\":\"09:30\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Booking slot is not available"));

        mockMvc.perform(put("/api/v1/bookings/" + firstBookingId + "/reschedule")
                        .header("Authorization", "Bearer " + otherOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-07\",\"startTime\":\"10:00\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + secondBookingId + "/cancel")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/bookings/" + secondBookingId + "/reschedule")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-07\",\"startTime\":\"10:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cancelled booking cannot be rescheduled"));
    }

    @Test
    void rescheduleToAnotherDayClearsPreviousReminderForTheReminderJob() throws Exception {
        Fixture fixture = fixture("booking-reschedule-reminder");
        String bookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.markReminderSent(Instant.parse("2026-09-06T12:00:00Z"));
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(put("/api/v1/bookings/" + bookingId + "/reschedule")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-10\",\"startTime\":\"09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-09-10T12:00:00Z"));

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getReminderSentAt()).isNull();
    }

    @Test
    void businessOwnerListsOwnBookingsAndAnotherUserCannot() throws Exception {
        Fixture fixture = fixture("booking-list");
        String otherOwnerToken = registerAndGetToken("booking-list-other-owner@example.com", "BUSINESS");
        String firstBookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        String secondBookingId = createBooking(fixture.customerToken(), fixture, "09:30");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.maxSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.sort").value("startsAt:asc,id:asc"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].id").value(firstBookingId))
                .andExpect(jsonPath("$.results[0].customerName").value("Cliente " + fixture.prefix()))
                .andExpect(jsonPath("$.results[0].customerPhone").value("+54 11 5555-1234"))
                .andExpect(jsonPath("$.results[1].id").value(secondBookingId));

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bookings can only be viewed by the business owner or an admin"));
    }

    @Test
    void businessBookingListSupportsPagination() throws Exception {
        Fixture fixture = fixture("booking-page");
        String firstBookingId = createBooking(fixture.customerToken(), fixture, "09:00");
        createBooking(fixture.customerToken(), fixture, "09:30");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings?page=0&size=1")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.maxSize").value(50))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.sort").value("startsAt:asc,id:asc"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(firstBookingId));
    }

    @Test
    void businessBookingListFiltersByRequestedLocalDate() throws Exception {
        Fixture fixture = fixture("booking-date-filter");
        String currentDayBookingId = createBooking(fixture.customerToken(), fixture, "2026-08-21", "09:00");
        String futureBookingId = createBooking(fixture.customerToken(), fixture, "2026-08-27", "09:30");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings?date=2026-08-21")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.sort").value("startsAt:asc,id:asc"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(currentDayBookingId));

        assertThat(futureBookingId).isNotEqualTo(currentDayBookingId);
    }

    @Test
    void businessBookingListCanBeFilteredByLocalDateRange() throws Exception {
        Fixture fixture = fixture("booking-date-range-filter");
        String mondayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");
        String thursdayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-10", "09:30");
        String fridayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-11", "10:00");
        String nextWeekBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-14", "09:00");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .param("dateFrom", "2026-09-07")
                        .param("dateTo", "2026-09-13")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.sort").value("startsAt:asc,id:asc"))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].id").value(mondayBookingId))
                .andExpect(jsonPath("$.results[1].id").value(thursdayBookingId))
                .andExpect(jsonPath("$.results[2].id").value(fridayBookingId));

        assertThat(nextWeekBookingId).isNotIn(mondayBookingId, thursdayBookingId, fridayBookingId);
    }

    @Test
    void businessBookingListRejectsInvalidLocalDateRange() throws Exception {
        Fixture fixture = fixture("booking-invalid-date-range");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .param("dateFrom", "2026-09-13")
                        .param("dateTo", "2026-09-07")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dateFrom must be before or equal to dateTo"));
    }

    @Test
    void businessOwnerCanCopyAvailableWeekBookings() throws Exception {
        Fixture fixture = fixture("booking-copy-week");
        enableWeeklyBookingCopy(fixture.ownerToken(), fixture.businessId());
        String mondayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");
        String thursdayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-10", "09:30");

        mockMvc.perform(post("/api/v1/businesses/" + fixture.businessId() + "/bookings/copy-week")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(copyWeekJson("2026-09-07", "2026-09-14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceWeekStart").value("2026-09-07"))
                .andExpect(jsonPath("$.targetWeekStart").value("2026-09-14"))
                .andExpect(jsonPath("$.sourceBookings").value(2))
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.created[0].sourceBookingId").value(mondayBookingId))
                .andExpect(jsonPath("$.created[0].date").value("2026-09-14"))
                .andExpect(jsonPath("$.created[0].startsAt").value("09:00:00"))
                .andExpect(jsonPath("$.created[1].sourceBookingId").value(thursdayBookingId))
                .andExpect(jsonPath("$.created[1].date").value("2026-09-17"))
                .andExpect(jsonPath("$.created[1].startsAt").value("09:30:00"));
    }

    @Test
    void businessWeekCopySkipsEquivalentBookingsWithoutDuplicating() throws Exception {
        Fixture fixture = fixture("booking-copy-week-duplicate");
        enableWeeklyBookingCopy(fixture.ownerToken(), fixture.businessId());
        createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");

        mockMvc.perform(post("/api/v1/businesses/" + fixture.businessId() + "/bookings/copy-week")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(copyWeekJson("2026-09-07", "2026-09-14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));

        mockMvc.perform(post("/api/v1/businesses/" + fixture.businessId() + "/bookings/copy-week")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(copyWeekJson("2026-09-07", "2026-09-14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBookings").value(1))
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.skipped[0].reason").value("Equivalent booking already exists"));
    }

    @Test
    void businessWeekCopyRejectsDisabledConfiguration() throws Exception {
        Fixture fixture = fixture("booking-copy-week-disabled");
        createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");

        mockMvc.perform(post("/api/v1/businesses/" + fixture.businessId() + "/bookings/copy-week")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(copyWeekJson("2026-09-07", "2026-09-14")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Weekly booking copy is not enabled for this business"));
    }

    @Test
    void businessWeekCopyReportsConflictsExplicitly() throws Exception {
        Fixture fixture = fixture("booking-copy-week-conflict");
        enableWeeklyBookingCopy(fixture.ownerToken(), fixture.businessId());
        String sourceBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");
        String secondOfferingId = createOffering(
                fixture.ownerToken(),
                fixture.businessId(),
                fixture.branchId(),
                "Servicio booking-copy-week-conflict-other"
        );
        updateResourceServices(
                fixture.ownerToken(),
                fixture.resourceId(),
                "Recurso " + fixture.prefix(),
                fixture.serviceOfferingId(),
                secondOfferingId
        );
        Fixture secondService = new Fixture(
                "booking-copy-week-conflict-other",
                fixture.ownerToken(),
                fixture.customerToken(),
                fixture.businessId(),
                fixture.branchId(),
                secondOfferingId,
                fixture.resourceId()
        );
        createBooking(secondService.customerToken(), secondService, "2026-09-14", "09:00");

        mockMvc.perform(post("/api/v1/businesses/" + fixture.businessId() + "/bookings/copy-week")
                        .header("Authorization", "Bearer " + fixture.ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(copyWeekJson("2026-09-07", "2026-09-14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBookings").value(1))
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflicts[0].sourceBookingId").value(sourceBookingId))
                .andExpect(jsonPath("$.conflicts[0].date").value("2026-09-14"))
                .andExpect(jsonPath("$.conflicts[0].startsAt").value("09:00:00"))
                .andExpect(jsonPath("$.conflicts[0].reason").value("Slot is not available"));
    }

    @Test
    void businessBookingListCanBeFilteredByBranch() throws Exception {
        Fixture firstBranch = fixture("booking-branch-filter");
        String secondBranchId = createBranch(
                firstBranch.ownerToken(),
                firstBranch.businessId(),
                "Sucursal booking-branch-filter-other"
        );
        String secondOfferingId = createOffering(
                firstBranch.ownerToken(),
                firstBranch.businessId(),
                secondBranchId,
                "Servicio booking-branch-filter-other"
        );
        String secondResourceId = createResource(
                firstBranch.ownerToken(),
                secondBranchId,
                "Recurso booking-branch-filter-other",
                secondOfferingId
        );
        Fixture secondBranch = new Fixture(
                "booking-branch-filter-other",
                firstBranch.ownerToken(),
                firstBranch.customerToken(),
                firstBranch.businessId(),
                secondBranchId,
                secondOfferingId,
                secondResourceId
        );
        String firstBranchBookingId = createBooking(firstBranch.customerToken(), firstBranch, "2026-09-07", "09:00");
        String secondBranchBookingId = createBooking(secondBranch.customerToken(), secondBranch, "2026-09-07", "09:00");

        mockMvc.perform(get("/api/v1/businesses/" + firstBranch.businessId() + "/bookings")
                        .param("date", "2026-09-07")
                        .param("branchId", secondBranchId)
                        .header("Authorization", "Bearer " + firstBranch.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(secondBranchBookingId))
                .andExpect(jsonPath("$.results[0].branchId").value(secondBranchId));

        assertThat(firstBranchBookingId).isNotEqualTo(secondBranchBookingId);
    }

    @Test
    void businessBookingListCanBeFilteredByResourceAndServiceOffering() throws Exception {
        Fixture fixture = fixture("booking-resource-service-filter");
        String secondResourceId = createResource(
                fixture.ownerToken(),
                fixture.branchId(),
                "Recurso booking-resource-service-filter-other",
                fixture.serviceOfferingId()
        );
        Fixture secondResource = new Fixture(
                "booking-resource-service-filter",
                fixture.ownerToken(),
                fixture.customerToken(),
                fixture.businessId(),
                fixture.branchId(),
                fixture.serviceOfferingId(),
                secondResourceId
        );
        String secondOfferingId = createOffering(
                fixture.ownerToken(),
                fixture.businessId(),
                fixture.branchId(),
                "Servicio booking-resource-service-filter-other"
        );
        String thirdResourceId = createResource(
                fixture.ownerToken(),
                fixture.branchId(),
                "Recurso booking-resource-service-filter-third",
                secondOfferingId
        );
        Fixture secondService = new Fixture(
                "booking-resource-service-filter-other",
                fixture.ownerToken(),
                fixture.customerToken(),
                fixture.businessId(),
                fixture.branchId(),
                secondOfferingId,
                thirdResourceId
        );
        String firstBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");
        String secondResourceBookingId = createBooking(secondResource.customerToken(), secondResource, "2026-09-07", "09:00");
        String secondServiceBookingId = createBooking(secondService.customerToken(), secondService, "2026-09-07", "09:30");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .param("date", "2026-09-07")
                        .param("resourceId", secondResourceId)
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(secondResourceBookingId))
                .andExpect(jsonPath("$.results[0].resourceId").value(secondResourceId));

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings")
                        .param("serviceOfferingId", secondOfferingId)
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(secondServiceBookingId))
                .andExpect(jsonPath("$.results[0].serviceOfferingId").value(secondOfferingId));

        assertThat(firstBookingId).isNotIn(secondResourceBookingId, secondServiceBookingId);
    }

    @Test
    void unavailableBookingReturnsConflict() throws Exception {
        Fixture fixture = fixture("booking-conflict");
        createBooking(fixture.customerToken(), fixture, "09:00");

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + fixture.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(fixture, "09:15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Booking slot is not available"));
    }

    @Test
    void concurrentRequestsCannotConfirmTheSameResourceSlot() throws Exception {
        Fixture fixture = fixture("booking-concurrent");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> request = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/v1/bookings")
                            .header("Authorization", "Bearer " + fixture.customerToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingJson(fixture, "09:00")))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(request);
            var second = executor.submit(request);
            ready.await();
            start.countDown();

            List<Integer> statuses = List.of(first.get(), second.get());

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(bookingRepository.findAll().stream()
                    .filter(booking -> booking.getResource().getId().equals(UUID.fromString(fixture.resourceId())))
                    .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                    .count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture fixture(String prefix) throws Exception {
        String ownerToken = registerAndGetToken(prefix + "-owner@example.com", "BUSINESS");
        String customerToken = registerAndGetToken(prefix + "-customer@example.com", "CUSTOMER");
        String businessId = createBusiness(ownerToken, "Negocio " + prefix);
        String branchId = createBranch(ownerToken, businessId, "Sucursal " + prefix);
        String serviceOfferingId = createOffering(ownerToken, businessId, branchId, "Servicio " + prefix);
        String resourceId = createResource(ownerToken, branchId, "Recurso " + prefix, serviceOfferingId);
        return new Fixture(prefix, ownerToken, customerToken, businessId, branchId, serviceOfferingId, resourceId);
    }

    private String createBooking(String token, Fixture fixture, String startsAt) throws Exception {
        return createBooking(token, fixture, "2026-09-07", startsAt);
    }

    private String createBooking(String token, Fixture fixture, String date, String startsAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(fixture, date, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price").value(1500.00))
                .andExpect(jsonPath("$.serviceName").value("Servicio " + fixture.prefix()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "%s"
                                }
                                """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String createBusiness(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void enableWeeklyBookingCopy(String token, String businessId) throws Exception {
        mockMvc.perform(put("/api/v1/businesses/" + businessId + "/configuration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "weeklyBookingCopyEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyBookingCopyEnabled").value(true));
    }

    private void enableDeposits(String token, String businessId) throws Exception {
        mockMvc.perform(put("/api/v1/businesses/" + businessId + "/configuration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyBookingCopyEnabled\":false,\"depositEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositEnabled").value(true));
    }

    private String createBranch(String token, String businessId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "Av. Siempre Viva 123",
                                  "locality": "Buenos Aires",
                                  "province": "CABA",
                                  "country": "Argentina",
                                  "latitude": -34.6037000,
                                  "longitude": -58.3816000,
                                  "zoneId": "America/Argentina/Buenos_Aires",
                                  "weeklySchedule": [
                                    {
                                      "dayOfWeek": "MONDAY",
                                      "intervals": [
                                        {"opensAt": "09:00", "closesAt": "12:00"}
                                      ]
                                    },
                                    {
                                      "dayOfWeek": "THURSDAY",
                                      "intervals": [
                                        {"opensAt": "09:00", "closesAt": "12:00"}
                                      ]
                                    },
                                    {
                                      "dayOfWeek": "FRIDAY",
                                      "intervals": [
                                        {"opensAt": "09:00", "closesAt": "12:00"}
                                      ]
                                    }
                                  ]
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createOffering(String token, String businessId, String branchId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "durationMinutes": 30,
                                  "price": 1500.00,
                                  "branchId": "%s",
                                  "status": "ACTIVE"
                                }
                                """.formatted(name, branchId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createResource(String token, String branchId, String name, String serviceOfferingId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visibleName": "%s",
                                  "type": "EMPLOYEE",
                                  "status": "ACTIVE",
                                  "serviceOfferingIds": ["%s"],
                                  "weeklySchedule": [
                                    {
                                      "dayOfWeek": "MONDAY",
                                      "intervals": [
                                        {"startsAt": "09:00", "endsAt": "12:00"}
                                      ]
                                    },
                                    {
                                      "dayOfWeek": "THURSDAY",
                                      "intervals": [
                                        {"startsAt": "09:00", "endsAt": "12:00"}
                                      ]
                                    },
                                    {
                                      "dayOfWeek": "FRIDAY",
                                      "intervals": [
                                        {"startsAt": "09:00", "endsAt": "12:00"}
                                      ]
                                    }
                                  ],
                                  "absences": []
                                }
                                """.formatted(name, serviceOfferingId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void updateResourceServices(
            String token,
            String resourceId,
            String name,
            String... serviceOfferingIds
    ) throws Exception {
        mockMvc.perform(put("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson(name, serviceOfferingIds)))
                .andExpect(status().isOk());
    }

    private String resourceJson(String name, String... serviceOfferingIds) {
        String serviceIds = Arrays.stream(serviceOfferingIds)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        return """
                {
                  "visibleName": "%s",
                  "type": "EMPLOYEE",
                  "status": "ACTIVE",
                  "serviceOfferingIds": [%s],
                  "weeklySchedule": [
                    {
                      "dayOfWeek": "MONDAY",
                      "intervals": [
                        {"startsAt": "09:00", "endsAt": "12:00"}
                      ]
                    },
                    {
                      "dayOfWeek": "THURSDAY",
                      "intervals": [
                        {"startsAt": "09:00", "endsAt": "12:00"}
                      ]
                    },
                    {
                      "dayOfWeek": "FRIDAY",
                      "intervals": [
                        {"startsAt": "09:00", "endsAt": "12:00"}
                      ]
                    }
                  ],
                  "absences": []
                }
                """.formatted(name, serviceIds);
    }

    private String bookingJson(Fixture fixture, String startsAt) {
        return bookingJson(fixture, "2026-09-07", startsAt);
    }

    private String publicBookingJsonWithEmail(Fixture fixture, String startsAt) {
        return """
                {
                  "branchId": "%s",
                  "serviceOfferingId": "%s",
                  "resourceId": "%s",
                  "date": "2026-09-07",
                  "startsAt": "%s",
                  "customerName": "Cliente %s",
                  "customerPhone": "+54 11 5555-1234",
                  "customerEmail": "cliente@example.com"
                }
                """.formatted(
                fixture.branchId(),
                fixture.serviceOfferingId(),
                fixture.resourceId(),
                startsAt,
                fixture.prefix()
        );
    }

    private String bookingJson(Fixture fixture, String startsAt, boolean skipCustomerContact) {
        return """
                {
                  "branchId": "%s",
                  "serviceOfferingId": "%s",
                  "resourceId": "%s",
                  "date": "2026-09-07",
                  "startsAt": "%s",
                  "customerName": "Cliente %s",
                  "customerPhone": "+54 11 5555-1234",
                  "skipCustomerContact": %s
                }
                """.formatted(
                fixture.branchId(),
                fixture.serviceOfferingId(),
                fixture.resourceId(),
                startsAt,
                fixture.prefix(),
                skipCustomerContact
        );
    }

    private String bookingJson(Fixture fixture, String date, String startsAt) {
        return """
                {
                  "branchId": "%s",
                  "serviceOfferingId": "%s",
                  "resourceId": "%s",
                  "date": "%s",
                  "startsAt": "%s",
                  "customerName": "Cliente %s",
                  "customerPhone": "+54 11 5555-1234"
                }
                """.formatted(
                fixture.branchId(),
                fixture.serviceOfferingId(),
                fixture.resourceId(),
                date,
                startsAt,
                fixture.prefix()
        );
    }

    private String bookingJsonWithDepositPaid(Fixture fixture, String startsAt, boolean depositPaid) {
        return """
                {
                  "branchId": "%s",
                  "serviceOfferingId": "%s",
                  "resourceId": "%s",
                  "date": "2026-09-07",
                  "startsAt": "%s",
                  "customerName": "Cliente %s",
                  "customerPhone": "+54 11 5555-1234",
                  "depositPaid": %s
                }
                """.formatted(
                fixture.branchId(),
                fixture.serviceOfferingId(),
                fixture.resourceId(),
                startsAt,
                fixture.prefix(),
                depositPaid
        );
    }

    private String copyWeekJson(String sourceWeekStart, String targetWeekStart) {
        return """
                {
                  "sourceWeekStart": "%s",
                  "targetWeekStart": "%s"
                }
                """.formatted(sourceWeekStart, targetWeekStart);
    }

    private record Fixture(
            String prefix,
            String ownerToken,
            String customerToken,
            String businessId,
            String branchId,
            String serviceOfferingId,
            String resourceId
    ) {
    }
}


