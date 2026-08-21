package com.turnero.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.business.BusinessRepository;
import com.turnero.business.BusinessStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PublicAvailabilityControllerIntegrationTests {

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
    private BusinessRepository businessRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void anonymousUserSearchesAvailabilityGroupedByBusinessAndBranch() throws Exception {
        Fixture haircut = fixture("market-haircut", "Corte express", "Palermo", "ACTIVE", "ACTIVE", true);
        Fixture massage = fixture("market-massage", "Masaje relax", "Recoleta", "ACTIVE", "ACTIVE", true);
        String customerToken = registerAndGetToken("market-customer@example.com", "CUSTOMER");
        createBooking(customerToken, haircut, "09:00");

        JsonNode response = search("""
                /api/v1/public/availability?date=2026-09-07&q=corte&locality=Palermo&startsFrom=09:00&startsTo=09:30&maxSlotsPerService=3
                """);

        assertThat(response.get("results")).hasSize(1);
        assertThat(response.at("/results/0/name").asText()).isEqualTo("Negocio market-haircut");
        assertThat(response.at("/results/0/branches/0/locality").asText()).isEqualTo("Palermo");
        assertThat(response.at("/results/0/branches/0/services/0/name").asText()).isEqualTo("Corte express");
        assertThat(slotStarts(response.at("/results/0/branches/0/services/0/slots")))
                .containsExactly("09:30:00");
        assertThat(response.toString()).doesNotContain(massage.businessId());
    }

    @Test
    void searchOnlyReturnsActiveBusinessesBranchesAndServices() throws Exception {
        fixture("market-active", "Servicio visible", "Palermo", "ACTIVE", "ACTIVE", true);
        Fixture inactiveBranch = fixture("market-inactive-branch", "Servicio oculto branch", "Palermo", "INACTIVE", "ACTIVE", true);
        Fixture inactiveService = fixture("market-inactive-service", "Servicio oculto service", "Palermo", "ACTIVE", "INACTIVE", true);
        Fixture inactiveBusiness = fixture("market-inactive-business", "Servicio oculto business", "Palermo", "ACTIVE", "ACTIVE", true);
        suspendBusiness(inactiveBusiness.businessId());

        JsonNode response = search("/api/v1/public/availability?date=2026-09-07&locality=Palermo&size=10");

        String body = response.toString();
        assertThat(body).contains("Servicio visible");
        assertThat(body).doesNotContain(inactiveBranch.businessId());
        assertThat(body).doesNotContain(inactiveService.businessId());
        assertThat(body).doesNotContain(inactiveBusiness.businessId());
    }

    @Test
    void searchSkipsServicesWithoutAvailabilityAndSupportsMultipleResults() throws Exception {
        fixture("market-first", "Corte premium", "Belgrano", "ACTIVE", "ACTIVE", true);
        fixture("market-second", "Corte barba", "Belgrano", "ACTIVE", "ACTIVE", true);
        Fixture withoutResource = fixture("market-empty", "Corte sin agenda", "Belgrano", "ACTIVE", "ACTIVE", false);

        JsonNode response = search("/api/v1/public/availability?date=2026-09-07&service=corte&locality=Belgrano&size=10");

        assertThat(response.get("results")).hasSize(2);
        assertThat(response.toString()).doesNotContain(withoutResource.businessId());
    }

    @Test
    void searchServiceNameIgnoresCaseAndAccents() throws Exception {
        Fixture accentedService = fixture("market-accented-service", "MÁSÁJE relax", "Almagro", "ACTIVE", "ACTIVE", true);
        Fixture plainService = fixture("market-plain-service", "Masaje descontracturante", "Villa Crespo", "ACTIVE", "ACTIVE", true);

        JsonNode unaccentedSearch = search("/api/v1/public/availability?date=2026-09-07&service=masaje&locality=Almagro");
        JsonNode accentedSearch = search("/api/v1/public/availability?date=2026-09-07&service=másaje&locality=Villa%20Crespo");

        assertThat(unaccentedSearch.get("results")).hasSize(1);
        assertThat(unaccentedSearch.at("/results/0/id").asText()).isEqualTo(accentedService.businessId());
        assertThat(accentedSearch.get("results")).hasSize(1);
        assertThat(accentedSearch.at("/results/0/id").asText()).isEqualTo(plainService.businessId());
    }

    @Test
    void searchCanBeFilteredByBusinessId() throws Exception {
        Fixture requestedBusiness = fixture("market-business-filter", "Corte clasico", "Chacarita", "ACTIVE", "ACTIVE", true);
        Fixture otherBusiness = fixture("market-business-filter-other", "Corte premium", "Chacarita", "ACTIVE", "ACTIVE", true);

        JsonNode response = search("/api/v1/public/availability?date=2026-09-07&locality=Chacarita&businessId="
                + requestedBusiness.businessId());

        assertThat(response.get("results")).hasSize(1);
        assertThat(response.at("/results/0/id").asText()).isEqualTo(requestedBusiness.businessId());
        assertThat(response.toString()).doesNotContain(otherBusiness.businessId());
    }

    @Test
    void searchLimitsAvailabilitySlotsPerServiceKeepingCurrentOrder() throws Exception {
        Fixture fixture = fixture("market-slot-pagination", "Corte agenda", "Caballito", "ACTIVE", "ACTIVE", true);

        JsonNode response = search("/api/v1/public/availability?date=2026-09-07&businessId="
                + fixture.businessId() + "&offset=0&limit=10&maxSlotsPerService=2");

        assertThat(response.get("offset").asInt()).isEqualTo(0);
        assertThat(response.get("limit").asInt()).isEqualTo(10);
        assertThat(response.get("totalMatchingServices").asInt()).isEqualTo(1);
        assertThat(response.get("totalAvailableSlots").asInt()).isEqualTo(4);
        assertThat(response.get("hasMore").asBoolean()).isFalse();
        assertThat(slotStarts(response.at("/results/0/branches/0/services/0/slots")))
                .containsExactly("09:00:00", "09:30:00");
    }

    @Test
    void searchPaginatesMatchingServicesAndLimitsSlotsPerService() throws Exception {
        Fixture fixture = fixture("market-multiple-services", "Sesion psiquiatrica", "Nuñez", "ACTIVE", "ACTIVE", true);
        String secondOfferingId = createOffering(
                fixture.ownerToken(),
                fixture.businessId(),
                fixture.branchId(),
                "Sesion psicologica",
                "ACTIVE"
        );
        createResource(fixture.ownerToken(), fixture.branchId(), "Recurso market-multiple-services-2", secondOfferingId);

        JsonNode response = search("/api/v1/public/availability?date=2026-09-07&service=sesion&locality=Nuñez&offset=0&limit=2&maxSlotsPerService=2&businessId="
                + fixture.businessId());

        JsonNode services = response.at("/results/0/branches/0/services");
        assertThat(services).hasSize(2);
        assertThat(serviceNames(services)).containsExactly("Sesion psicologica", "Sesion psiquiatrica");
        assertThat(services.get(0).get("slots")).hasSize(2);
        assertThat(services.get(1).get("slots")).hasSize(2);
        assertThat(response.get("totalMatchingServices").asInt()).isEqualTo(2);
        assertThat(response.get("totalAvailableSlots").asInt()).isEqualTo(8);
        assertThat(response.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void serviceSlotsEndpointPaginatesSlotsForOneService() throws Exception {
        Fixture fixture = fixture("market-service-slots", "Sesion psiquiatrica", "Flores", "ACTIVE", "ACTIVE", true);

        JsonNode response = search("/api/v1/public/availability/" + fixture.serviceOfferingId()
                + "/slots?date=2026-09-07&branchId=" + fixture.branchId()
                + "&startsFrom=09:00&startsTo=11:00&offset=2&limit=2");

        assertThat(response.get("serviceOfferingId").asText()).isEqualTo(fixture.serviceOfferingId());
        assertThat(response.get("branchId").asText()).isEqualTo(fixture.branchId());
        assertThat(response.get("offset").asInt()).isEqualTo(2);
        assertThat(response.get("limit").asInt()).isEqualTo(2);
        assertThat(response.get("totalAvailableSlots").asInt()).isEqualTo(4);
        assertThat(response.get("hasMore").asBoolean()).isFalse();
        assertThat(slotStarts(response.get("slots"))).containsExactly("10:00:00", "10:30:00");
    }

    @Test
    void searchRejectsAvailabilityLimitAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/public/availability")
                        .param("date", "2026-09-07")
                        .param("limit", "11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceSlotsEndpointRejectsLimitAboveMaximum() throws Exception {
        Fixture fixture = fixture("market-service-slots-limit", "Sesion psiquiatrica", "Flores", "ACTIVE", "ACTIVE", true);

        mockMvc.perform(get("/api/v1/public/availability/" + fixture.serviceOfferingId() + "/slots")
                        .param("date", "2026-09-07")
                        .param("branchId", fixture.branchId())
                        .param("limit", "11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRemainPrivateWhilePublicAvailabilityIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/public/availability")
                        .param("date", "2026-09-07"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode search(String uri) throws Exception {
        String response = mockMvc.perform(get(uri.strip()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private Fixture fixture(
            String prefix,
            String serviceName,
            String locality,
            String branchStatus,
            String serviceStatus,
            boolean createResource
    ) throws Exception {
        String ownerToken = registerAndGetToken(prefix + "-owner@example.com", "BUSINESS");
        String businessId = createBusiness(ownerToken, "Negocio " + prefix);
        String branchId = createBranch(ownerToken, businessId, "Sucursal " + prefix, locality, branchStatus);
        String serviceOfferingId = createOffering(ownerToken, businessId, branchId, serviceName, serviceStatus);
        String resourceId = createResource
                ? createResource(ownerToken, branchId, "Recurso " + prefix, serviceOfferingId)
                : null;
        return new Fixture(ownerToken, businessId, branchId, serviceOfferingId, resourceId);
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

    private String createBranch(
            String token,
            String businessId,
            String name,
            String locality,
            String status
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "Av. Corrientes 123",
                                  "locality": "%s",
                                  "province": "CABA",
                                  "country": "Argentina",
                                  "latitude": -34.6037000,
                                  "longitude": -58.3816000,
                                  "zoneId": "America/Argentina/Buenos_Aires",
                                  "status": "%s",
                                  "weeklySchedule": [
                                    {
                                      "dayOfWeek": "MONDAY",
                                      "intervals": [
                                        {"opensAt": "09:00", "closesAt": "11:00"}
                                      ]
                                    }
                                  ]
                                }
                                """.formatted(name, locality, status)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createOffering(
            String token,
            String businessId,
            String branchId,
            String name,
            String status
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Servicio publicable",
                                  "durationMinutes": 30,
                                  "price": 1500.00,
                                  "currency": "ARS",
                                  "branchId": "%s",
                                  "status": "%s"
                                }
                                """.formatted(name, branchId, status)))
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
                                        {"startsAt": "09:00", "endsAt": "11:00"}
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

    private void createBooking(String token, Fixture fixture, String startsAt) throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "branchId": "%s",
                                  "serviceOfferingId": "%s",
                                  "resourceId": "%s",
                                  "date": "2026-09-07",
                                  "startsAt": "%s",
                                  "customerName": "Cliente marketplace",
                                  "customerPhone": "+54 11 5555-6789"
                                }
                                """.formatted(fixture.branchId(), fixture.serviceOfferingId(), fixture.resourceId(), startsAt)))
                .andExpect(status().isCreated());
    }

    private void suspendBusiness(String businessId) {
        var business = businessRepository.findById(UUID.fromString(businessId)).orElseThrow();
        ReflectionTestUtils.setField(business, "status", BusinessStatus.SUSPENDED);
        businessRepository.saveAndFlush(business);
    }

    private List<String> slotStarts(JsonNode slots) {
        List<String> starts = new ArrayList<>();
        slots.forEach(slot -> starts.add(slot.get("startsAt").asText()));
        return starts;
    }

    private List<String> serviceNames(JsonNode services) {
        List<String> names = new ArrayList<>();
        services.forEach(service -> names.add(service.get("name").asText()));
        return names;
    }

    private record Fixture(
            String ownerToken,
            String businessId,
            String branchId,
            String serviceOfferingId,
            String resourceId
    ) {
    }
}
