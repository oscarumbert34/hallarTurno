package com.turnero.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.auth.JwtService;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import com.turnero.user.UserStatus;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "app.security.cors.allowed-origins=http://localhost:4200",
        "app.security.cors.allow-credentials=true"
})
@AutoConfigureMockMvc
class PrivateEndpointSecurityIntegrationTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void customerCannotCreateBusinessCatalogData() throws Exception {
        String customerToken = registerAndGetToken(uniqueEmail("customer"), "CUSTOMER");

        mockMvc.perform(post("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente intentando negocio\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only business users or admins can manage businesses"));

        mockMvc.perform(get("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isOk());
    }

    @Test
    void changingIdsDoesNotAllowCrossTenantBusinessBranchServiceOrResourceAccess() throws Exception {
        Tenant ownerTenant = tenant("idor-owner");
        Tenant otherTenant = tenant("idor-other");

        mockMvc.perform(get("/api/v1/businesses/" + ownerTenant.businessId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/businesses/" + ownerTenant.businessId() + "/branches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/branches/" + ownerTenant.branchId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/businesses/" + ownerTenant.businessId() + "/service-offerings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/service-offerings/" + ownerTenant.offeringId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/branches/" + ownerTenant.branchId() + "/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/resources/" + ownerTenant.resourceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTenant.ownerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageAnyTenantResourceExplicitly() throws Exception {
        Tenant tenant = tenant("admin-access");
        String adminToken = createAdminToken(uniqueEmail("admin"));

        mockMvc.perform(get("/api/v1/businesses/" + tenant.businessId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenant.businessId()));

        mockMvc.perform(put("/api/v1/branches/" + tenant.branchId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Sucursal editada por admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sucursal editada por admin"));

        mockMvc.perform(put("/api/v1/service-offerings/" + tenant.offeringId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Servicio editado por admin", tenant.branchId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Servicio editado por admin"));

        mockMvc.perform(put("/api/v1/resources/" + tenant.resourceId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Recurso editado por admin", tenant.offeringId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleName").value("Recurso editado por admin"));
    }

    @Test
    void bookingCancellationAllowsCustomerBusinessOwnerAndAdminOnly() throws Exception {
        Tenant tenant = tenant("booking-idor");
        String customerToken = registerAndGetToken(uniqueEmail("booking-customer"), "CUSTOMER");
        String otherCustomerToken = registerAndGetToken(uniqueEmail("booking-other"), "CUSTOMER");
        String adminToken = createAdminToken(uniqueEmail("booking-admin"));
        String firstBookingId = createBooking(customerToken, tenant, "09:00");
        String secondBookingId = createBooking(customerToken, tenant, "09:30");
        String thirdBookingId = createBooking(customerToken, tenant, "10:00");

        mockMvc.perform(post("/api/v1/bookings/" + firstBookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + secondBookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/" + thirdBookingId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void publicBookingEndpointAllowsAnonymousPost() throws Exception {
        mockMvc.perform(post("/api/v1/public/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicAvailabilitySlotsEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/availability/" + UUID.randomUUID() + "/slots")
                        .param("branchId", UUID.randomUUID().toString())
                        .param("date", "2026-09-07"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsPreflightUsesConfiguredOriginWithoutWildcardCredentials() throws Exception {
        mockMvc.perform(options("/api/v1/public/availability")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    private Tenant tenant(String prefix) throws Exception {
        String ownerToken = registerAndGetToken(uniqueEmail(prefix + "-owner"), "BUSINESS");
        String businessId = createBusiness(ownerToken, "Negocio " + prefix);
        String branchId = createBranch(ownerToken, businessId, "Sucursal " + prefix);
        String offeringId = createOffering(ownerToken, businessId, branchId, "Servicio " + prefix);
        String resourceId = createResource(ownerToken, branchId, "Recurso " + prefix, offeringId);
        return new Tenant(ownerToken, businessId, branchId, offeringId, resourceId);
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

    private String createAdminToken(String email) {
        User user = User.create(email, "hash", UserRole.ADMIN);
        user.updateStatus(UserStatus.ACTIVE);
        User saved = userRepository.saveAndFlush(user);
        return jwtService.generateAccessToken(saved);
    }

    private String createBusiness(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createOffering(String token, String businessId, String branchId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson(name, branchId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createResource(String token, String branchId, String name, String offeringId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson(name, offeringId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createBooking(String token, Tenant tenant, String startsAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "branchId": "%s",
                                  "serviceOfferingId": "%s",
                                  "resourceId": "%s",
                                  "date": "2026-09-07",
                                  "startsAt": "%s",
                                  "customerName": "Cliente seguridad",
                                  "customerPhone": "+54 11 5555-4321"
                                }
                                """.formatted(tenant.branchId(), tenant.offeringId(), tenant.resourceId(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String branchJson(String name) {
        return """
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
                """.formatted(name);
    }

    private String offeringJson(String name, String branchId) {
        return """
                {
                  "name": "%s",
                  "durationMinutes": 30,
                  "price": 1500.00,
                  "branchId": "%s",
                  "status": "ACTIVE"
                }
                """.formatted(name, branchId);
    }

    private String resourceJson(String visibleName, String offeringId) {
        return """
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
                """.formatted(visibleName, offeringId);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
    }

    private record Tenant(
            String ownerToken,
            String businessId,
            String branchId,
            String offeringId,
            String resourceId
    ) {
    }
}


