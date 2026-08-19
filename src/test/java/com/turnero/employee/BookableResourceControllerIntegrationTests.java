package com.turnero.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class BookableResourceControllerIntegrationTests {

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
    private BookableResourceRepository resourceRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void ownerCreatesResourceWithSeveralServicesAndAbsences() throws Exception {
        String token = registerAndGetToken("resource-owner@example.com");
        String businessId = createBusiness(token, "Recursos Centro");
        String branchId = createBranch(token, businessId, "Sucursal Recursos");
        String haircutId = createOffering(token, businessId, branchId, "Corte");
        String beardId = createOffering(token, businessId, null, "Barba");

        String resourceId = createResource(token, branchId, "Ana Perez", "EMPLOYEE", haircutId, beardId);

        mockMvc.perform(get("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleName").value("Ana Perez"))
                .andExpect(jsonPath("$.type").value("EMPLOYEE"))
                .andExpect(jsonPath("$.serviceOfferingIds.length()").value(2))
                .andExpect(jsonPath("$.weeklySchedule[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.weeklySchedule[0].intervals[0].startsAt").value("09:00:00"))
                .andExpect(jsonPath("$.absences[0].date").value("2026-09-01"));

        mockMvc.perform(get("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(resourceId));
    }

    @Test
    void serviceCanHaveSeveralResources() throws Exception {
        String token = registerAndGetToken("resource-many@example.com");
        String businessId = createBusiness(token, "Recursos Multiples");
        String branchId = createBranch(token, businessId, "Sucursal Multiple");
        String offeringId = createOffering(token, businessId, branchId, "Masaje");

        createResource(token, branchId, "Recurso Uno", "EMPLOYEE", offeringId);
        createResource(token, branchId, "Recurso Dos", "ROOM", offeringId);

        mockMvc.perform(get("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceOfferingIds[0]").value(offeringId))
                .andExpect(jsonPath("$[1].serviceOfferingIds[0]").value(offeringId));
    }

    @Test
    void rejectsInvalidSchedulesAbsencesAndCrossBusinessServices() throws Exception {
        String ownerToken = registerAndGetToken("resource-invalid-owner@example.com");
        String otherToken = registerAndGetToken("resource-invalid-other@example.com");
        String businessId = createBusiness(ownerToken, "Recursos Invalidos");
        String branchId = createBranch(ownerToken, businessId, "Sucursal Invalidos");
        String otherBusinessId = createBusiness(otherToken, "Otro Negocio");
        String otherBranchId = createBranch(otherToken, otherBusinessId, "Otra Sucursal");
        String otherOfferingId = createOffering(otherToken, otherBusinessId, otherBranchId, "Otro Servicio");

        mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Horario malo", "EMPLOYEE", "[]", invalidSchedule(), "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Resource schedule intervals overlap for MONDAY"));

        mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Ausencia mala", "EMPLOYEE", "[]", "[]", invalidAbsences())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Resource absences overlap for 2026-09-01"));

        mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Servicio ajeno", "EMPLOYEE", "[\"" + otherOfferingId + "\"]", "[]", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Service offering does not belong to the branch business"));
    }

    @Test
    void anotherOwnerCannotCreateUpdateOrDeactivateResource() throws Exception {
        String ownerToken = registerAndGetToken("resource-auth-owner@example.com");
        String otherToken = registerAndGetToken("resource-auth-other@example.com");
        String businessId = createBusiness(ownerToken, "Recursos Auth");
        String branchId = createBranch(ownerToken, businessId, "Sucursal Auth");
        String resourceId = createResource(ownerToken, branchId, "Owner Resource", "EMPLOYEE");

        mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Ajeno", "EMPLOYEE", "[]", "[]", "[]")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Ajeno", "EMPLOYEE", "[]", "[]", "[]")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerUpdatesAndDeactivatesResource() throws Exception {
        String token = registerAndGetToken("resource-update@example.com");
        String businessId = createBusiness(token, "Recursos Update");
        String branchId = createBranch(token, businessId, "Sucursal Update");
        String resourceId = createResource(token, branchId, "Nombre Viejo", "EMPLOYEE");

        mockMvc.perform(put("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson("Box 1", "ROOM", "[]", "[]", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleName").value("Box 1"))
                .andExpect(jsonPath("$.type").value("ROOM"));

        mockMvc.perform(delete("/api/v1/resources/" + resourceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(resourceRepository.findById(UUID.fromString(resourceId)))
                .isPresent()
                .get()
                .extracting(BookableResource::getStatus)
                .isEqualTo(BookableResourceStatus.INACTIVE);
    }

    @Test
    void protectedResourceEndpointsRejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/resources/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "BUSINESS"
                                }
                                """.formatted(email)))
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
                                  "weeklySchedule": []
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createOffering(String token, String businessId, String branchId, String name) throws Exception {
        String branchField = branchId == null ? "" : "\"branchId\":\"" + branchId + "\",";
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "durationMinutes": 30,
                                  "price": 1000.00,
                                  %s
                                  "status": "ACTIVE"
                                }
                                """.formatted(name, branchField)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createResource(String token, String branchId, String name, String type, String... serviceOfferingIds)
            throws Exception {
        String serviceOfferingIdsJson = java.util.Arrays.stream(serviceOfferingIds)
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String response = mockMvc.perform(post("/api/v1/branches/" + branchId + "/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceJson(name, type, serviceOfferingIdsJson, validSchedule(), validAbsences())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String resourceJson(
            String visibleName,
            String type,
            String serviceOfferingIds,
            String weeklySchedule,
            String absences
    ) {
        return """
                {
                  "visibleName": "%s",
                  "type": "%s",
                  "status": "ACTIVE",
                  "serviceOfferingIds": %s,
                  "weeklySchedule": %s,
                  "absences": %s
                }
                """.formatted(visibleName, type, serviceOfferingIds, weeklySchedule, absences);
    }

    private String validSchedule() {
        return """
                [
                  {
                    "dayOfWeek": "MONDAY",
                    "intervals": [
                      {"startsAt": "09:00", "endsAt": "12:00"},
                      {"startsAt": "14:00", "endsAt": "18:00"}
                    ]
                  }
                ]
                """;
    }

    private String invalidSchedule() {
        return """
                [
                  {
                    "dayOfWeek": "MONDAY",
                    "intervals": [
                      {"startsAt": "09:00", "endsAt": "12:00"},
                      {"startsAt": "11:00", "endsAt": "13:00"}
                    ]
                  }
                ]
                """;
    }

    private String validAbsences() {
        return """
                [
                  {"date": "2026-09-01", "startsAt": "10:00", "endsAt": "11:00"}
                ]
                """;
    }

    private String invalidAbsences() {
        return """
                [
                  {"date": "2026-09-01", "startsAt": "10:00", "endsAt": "12:00"},
                  {"date": "2026-09-01", "startsAt": "11:00", "endsAt": "13:00"}
                ]
                """;
    }
}
