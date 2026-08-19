package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.auth.JwtService;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import com.turnero.user.UserStatus;
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
class BusinessControllerIntegrationTests {

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
    void ownerCreatesAndReadsBusinessWithoutOwnerSensitiveData() throws Exception {
        String token = registerAndGetToken("owner-business@example.com", "BUSINESS");

        String response = mockMvc.perform(post("/api/v1/businesses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cafe Central",
                                  "shortDescription": "Turnos para merienda",
                                  "phone": "+54 11 5555-5555",
                                  "contactEmail": "hola@cafecentral.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cafe Central"))
                .andExpect(jsonPath("$.slug").value("cafe-central"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.owner.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String businessId = json.get("id").asText();
        assertThat(json.toString()).doesNotContain("passwordHash", "hash");

        mockMvc.perform(get("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(businessId));

        mockMvc.perform(get("/api/v1/businesses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + businessId + "')]").exists())
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());
    }

    @Test
    void anotherUserCannotUpdateOwnerBusinessButAdminCan() throws Exception {
        String ownerToken = registerAndGetToken("owner-update@example.com", "BUSINESS");
        String otherToken = registerAndGetToken("other-update@example.com", "BUSINESS");
        String businessId = createBusiness(ownerToken, "Estudio Norte");

        mockMvc.perform(put("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Intento ajeno"
                                }
                                """))
                .andExpect(status().isForbidden());

        String adminToken = createAdminToken("admin-update@example.com");
        mockMvc.perform(put("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Editado por admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Editado por admin"));
    }

    @Test
    void slugIsUniqueForCollidingBusinessNames() throws Exception {
        String token = registerAndGetToken("slug-owner@example.com", "BUSINESS");

        String firstId = createBusiness(token, "La Barberia");
        String secondId = createBusiness(token, "La Barberia");

        mockMvc.perform(get("/api/v1/businesses/" + firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("la-barberia"));

        mockMvc.perform(get("/api/v1/businesses/" + secondId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("la-barberia-2"));
    }

    @Test
    void publicListWorksWithoutTokenButProtectedCrudRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/businesses"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sin token\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/businesses/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanDeleteBusiness() throws Exception {
        String token = registerAndGetToken("delete-owner@example.com", "BUSINESS");
        String businessId = createBusiness(token, "Borrar Centro");

        mockMvc.perform(delete("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
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
}
