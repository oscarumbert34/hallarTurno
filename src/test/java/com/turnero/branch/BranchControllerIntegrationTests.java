package com.turnero.branch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class BranchControllerIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero")
            .withUsername("turnero")
            .withPassword("turnero");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void ownerCreatesMultipleBranchesAndReadsWeeklySchedule() throws Exception {
        String token = registerAndGetToken("branch-owner@example.com");
        String businessId = createBusiness(token, "Clinica Sur");

        String firstBranchId = createBranch(token, businessId, "Sede Centro");
        String secondBranchId = createBranch(token, businessId, "Sede Norte");

        mockMvc.perform(get("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].businessId").value(businessId));

        mockMvc.perform(get("/api/v1/branches/" + firstBranchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sede Centro"))
                .andExpect(jsonPath("$.weeklySchedule[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.weeklySchedule[0].intervals[0].opensAt").value("09:00:00"))
                .andExpect(jsonPath("$.weeklySchedule[1].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.weeklySchedule[1].intervals.length()").value(0));

        mockMvc.perform(get("/api/v1/branches/" + secondBranchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sede Norte"));
    }

    @Test
    void rejectsInvalidAndOverlappingSchedules() throws Exception {
        String token = registerAndGetToken("branch-invalid@example.com");
        String businessId = createBusiness(token, "Agenda Invalidos");

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Solapada", """
                                [
                                  {
                                    "dayOfWeek": "MONDAY",
                                    "intervals": [
                                      {"opensAt": "09:00", "closesAt": "12:00"},
                                      {"opensAt": "11:00", "closesAt": "15:00"}
                                    ]
                                  }
                                ]
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Schedule intervals overlap for MONDAY"));

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Invertida", """
                                [
                                  {
                                    "dayOfWeek": "MONDAY",
                                    "intervals": [
                                      {"opensAt": "12:00", "closesAt": "09:00"}
                                    ]
                                  }
                                ]
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Schedule interval start must be before end"));
    }

    @Test
    void anotherOwnerCannotCreateUpdateOrDeleteBranchForDifferentBusiness() throws Exception {
        String ownerToken = registerAndGetToken("branch-owner-auth@example.com");
        String otherToken = registerAndGetToken("branch-other-auth@example.com");
        String businessId = createBusiness(ownerToken, "Negocio Owner");
        String branchId = createBranch(ownerToken, businessId, "Sucursal Owner");

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/branches")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Ajena", "[]")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Ajena", "[]")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerUpdatesAndDeletesBranch() throws Exception {
        String token = registerAndGetToken("branch-update@example.com");
        String businessId = createBusiness(token, "Negocio Update");
        String branchId = createBranch(token, businessId, "Sucursal Vieja");

        mockMvc.perform(put("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Sucursal Nueva", """
                                [
                                  {
                                    "dayOfWeek": "WEDNESDAY",
                                    "intervals": [
                                      {"opensAt": "10:00", "closesAt": "16:00"}
                                    ]
                                  }
                                ]
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sucursal Nueva"))
                .andExpect(jsonPath("$.weeklySchedule[2].intervals[0].opensAt").value("10:00:00"));

        mockMvc.perform(put("/api/v1/businesses/" + businessId + "/branches/" + branchId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchJson("Sucursal Ruta Anidada", """
                                [
                                  {
                                    "dayOfWeek": "THURSDAY",
                                    "intervals": [
                                      {"opensAt": "11:00", "closesAt": "17:00"}
                                    ]
                                  }
                                ]
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sucursal Ruta Anidada"))
                .andExpect(jsonPath("$.weeklySchedule[3].intervals[0].opensAt").value("11:00:00"));

        mockMvc.perform(delete("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedBranchEndpointsRejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/branches/00000000-0000-0000-0000-000000000000"))
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
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
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
                        .content(branchJson(name, """
                                [
                                  {
                                    "dayOfWeek": "MONDAY",
                                    "intervals": [
                                      {"opensAt": "09:00", "closesAt": "12:00"},
                                      {"opensAt": "14:00", "closesAt": "18:00"}
                                    ]
                                  },
                                  {
                                    "dayOfWeek": "TUESDAY",
                                    "intervals": []
                                  }
                                ]
                                """)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String branchJson(String name, String scheduleJson) {
        return """
                {
                  "name": "%s",
                  "address": "Av. Siempre Viva 123",
                  "locality": "Buenos Aires",
                  "province": "CABA",
                  "country": "Argentina",
                  "latitude": -34.6037000,
                  "longitude": -58.3816000,
                  "status": "ACTIVE",
                  "weeklySchedule": %s
                }
                """.formatted(name, scheduleJson);
    }
}
