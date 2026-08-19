package com.turnero.service;

import static org.assertj.core.api.Assertions.assertThat;
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
class ServiceOfferingControllerIntegrationTests {

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
    private ServiceOfferingRepository offeringRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void ownerCreatesListsUpdatesAndDeactivatesServiceOffering() throws Exception {
        String token = registerAndGetToken("offering-owner@example.com");
        String businessId = createBusiness(token, "Salon Offering");
        String branchId = createBranch(token, businessId, "Sede Offering");

        String offeringId = createOffering(token, businessId, branchId, "Corte clasico");

        mockMvc.perform(get("/api/v1/businesses/" + businessId + "/service-offerings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Corte clasico"))
                .andExpect(jsonPath("$[0].currency").value("ARS"))
                .andExpect(jsonPath("$[0].branchId").value(branchId));

        mockMvc.perform(put("/api/v1/service-offerings/" + offeringId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Corte premium", branchId, "45", "2200.50", "usd", "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corte premium"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.price").value(2200.50))
                .andExpect(jsonPath("$.currency").value("USD"));

        mockMvc.perform(delete("/api/v1/service-offerings/" + offeringId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(offeringRepository.findById(java.util.UUID.fromString(offeringId)))
                .isPresent()
                .get()
                .extracting(ServiceOffering::getStatus)
                .isEqualTo(ServiceOfferingStatus.INACTIVE);
    }

    @Test
    void validatesPriceDurationAndCurrency() throws Exception {
        String token = registerAndGetToken("offering-validation@example.com");
        String businessId = createBusiness(token, "Validaciones");

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Muy corto", null, "0", "100.00", "ARS", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Precio invalido", null, "30", "-1.00", "ARS", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Moneda invalida", null, "30", "100.00", "AR$", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void anotherOwnerCannotCreateUpdateOrDeactivateOfferings() throws Exception {
        String ownerToken = registerAndGetToken("offering-owner-auth@example.com");
        String otherToken = registerAndGetToken("offering-other-auth@example.com");
        String businessId = createBusiness(ownerToken, "Negocio Offering");
        String offeringId = createOffering(ownerToken, businessId, null, "Servicio owner");

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Ajeno", null, "30", "100.00", "ARS", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/service-offerings/" + offeringId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Ajeno", null, "30", "100.00", "ARS", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/service-offerings/" + offeringId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBranchFromAnotherBusiness() throws Exception {
        String ownerToken = registerAndGetToken("offering-branch-owner@example.com");
        String otherToken = registerAndGetToken("offering-branch-other@example.com");
        String businessId = createBusiness(ownerToken, "Negocio Principal");
        String otherBusinessId = createBusiness(otherToken, "Negocio Ajeno");
        String otherBranchId = createBranch(otherToken, otherBusinessId, "Sucursal Ajena");

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Servicio mal vinculado", otherBranchId, "30", "100.00", "ARS", "ACTIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Branch does not belong to the business"));
    }

    @Test
    void protectedOfferingEndpointsRejectMissingToken() throws Exception {
        String token = registerAndGetToken("offering-public-list@example.com");
        String businessId = createBusiness(token, "Servicios Publicos");

        mockMvc.perform(get("/api/v1/businesses/" + businessId + "/service-offerings"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson("Sin token", null, "30", "100.00", "ARS", "ACTIVE")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/service-offerings/00000000-0000-0000-0000-000000000000"))
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
        String response = mockMvc.perform(post("/api/v1/businesses/" + businessId + "/service-offerings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringJson(name, branchId, "30", "1500.00", null, "ACTIVE")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String offeringJson(
            String name,
            String branchId,
            String durationMinutes,
            String price,
            String currency,
            String status
    ) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"name\":\"").append(name).append("\",");
        json.append("\"description\":\"Servicio reservable\",");
        json.append("\"durationMinutes\":").append(durationMinutes).append(",");
        json.append("\"price\":").append(price).append(",");
        if (branchId != null) {
            json.append("\"branchId\":\"").append(branchId).append("\",");
        }
        if (currency != null) {
            json.append("\"currency\":\"").append(currency).append("\",");
        }
        json.append("\"status\":\"").append(status).append("\"");
        json.append("}");
        return json.toString();
    }
}
