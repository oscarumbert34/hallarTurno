package com.turnero.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
                        .content(bookingJson(fixture, "09:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.customerName").value("Cliente " + fixture.prefix()))
                .andExpect(jsonPath("$.customerPhone").value("+54 11 5555-1234"))
                .andExpect(jsonPath("$.serviceName").value("Servicio " + fixture.prefix()));
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
                .andExpect(jsonPath("$.totalElements").value(2))
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
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(firstBookingId));
    }

    @Test
    void businessBookingListFiltersByRequestedLocalDate() throws Exception {
        Fixture fixture = fixture("booking-date-filter");
        String currentDayBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-07", "09:00");
        String futureBookingId = createBooking(fixture.customerToken(), fixture, "2026-09-14", "09:30");

        mockMvc.perform(get("/api/v1/businesses/" + fixture.businessId() + "/bookings?date=2026-09-07")
                        .header("Authorization", "Bearer " + fixture.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value(currentDayBookingId));

        assertThat(futureBookingId).isNotEqualTo(currentDayBookingId);
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

    private String bookingJson(Fixture fixture, String startsAt) {
        return bookingJson(fixture, "2026-09-07", startsAt);
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


