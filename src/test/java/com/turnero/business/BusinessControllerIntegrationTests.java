package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.turnero.storage.ObjectStorageService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @MockBean
    private ObjectStorageService storageService;

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
                                  "category": "BEAUTY",
                                  "publicDescription": "Turnos para merienda",
                                  "aboutUs": "Una cafetería de barrio desde 1998",
                                  "whatsapp": "+54 9 11 5555-5555",
                                  "instagram": "@cafecentral",
                                  "phone": "+54 11 5555-5555",
                                  "contactEmail": "hola@cafecentral.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cafe Central"))
                .andExpect(jsonPath("$.category").value("BEAUTY"))
                .andExpect(jsonPath("$.shortDescription").value("Turnos para merienda"))
                .andExpect(jsonPath("$.publicDescription").value("Turnos para merienda"))
                .andExpect(jsonPath("$.aboutUs").value("Una cafetería de barrio desde 1998"))
                .andExpect(jsonPath("$.whatsapp").value("+54 9 11 5555-5555"))
                .andExpect(jsonPath("$.instagram").value("@cafecentral"))
                .andExpect(jsonPath("$.slug").value("cafe-central"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.depositEnabled").value(false))
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
    void ownerCanReadAndUpdateBusinessConfiguration() throws Exception {
        String token = registerAndGetToken("owner-configuration@example.com", "BUSINESS");
        String businessId = createBusiness(token, "Configuracion Centro");

        mockMvc.perform(get("/api/v1/businesses/" + businessId + "/configuration")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.weeklyBookingCopyEnabled").value(false))
                .andExpect(jsonPath("$.depositEnabled").value(false))
                .andExpect(jsonPath("$.appointmentConfirmationEnabled").value(false));

        mockMvc.perform(put("/api/v1/businesses/" + businessId + "/configuration")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "weeklyBookingCopyEnabled": true,
                                  "depositEnabled": true,
                                  "appointmentConfirmationEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.weeklyBookingCopyEnabled").value(true))
                .andExpect(jsonPath("$.depositEnabled").value(true))
                .andExpect(jsonPath("$.appointmentConfirmationEnabled").value(true));

        mockMvc.perform(get("/api/v1/businesses/" + businessId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositEnabled").value(true));

        mockMvc.perform(get("/api/v1/businesses/" + UUID.randomUUID() + "/configuration")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Business not found"));
    }

    @Test
    void ownerCanConfigurePublicProfileUploadImagesAndReadSignedPublicUrls() throws Exception {
        String token = registerAndGetToken("public-profile@example.com", "BUSINESS");
        String businessId = createBusiness(token, "Barberia Norte");
        when(storageService.signedGetUrl(anyString()))
                .thenAnswer(invocation -> "https://signed.example/" + invocation.getArgument(0, String.class));

        mockMvc.perform(put("/api/v1/businesses/" + businessId + "/public-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "publicDescription": "Cortes, barba y cuidado personal",
                                  "aboutUs": "Atencion personalizada",
                                  "whatsapp": "541112345678",
                                  "instagram": "barberianorte"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicDescription").value("Cortes, barba y cuidado personal"))
                .andExpect(jsonPath("$.instagram").value("barberianorte"));

        MockMultipartFile logo = new MockMultipartFile(
                "file", "logo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1});
        mockMvc.perform(multipart("/api/v1/businesses/" + businessId + "/public-profile/logo")
                        .file(logo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").isNotEmpty())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("https://signed.example/")))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        MockMultipartFile cover = new MockMultipartFile(
                "file", "cover.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1});
        mockMvc.perform(multipart("/api/v1/businesses/" + businessId + "/public-profile/cover")
                        .file(cover)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").isNotEmpty())
                .andExpect(jsonPath("$.contentType").value("image/webp"));

        mockMvc.perform(get("/api/v1/public/businesses/barberia-norte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicDescription").value("Cortes, barba y cuidado personal"))
                .andExpect(jsonPath("$.aboutUs").value("Atencion personalizada"))
                .andExpect(jsonPath("$.whatsapp").value("541112345678"))
                .andExpect(jsonPath("$.instagram").value("barberianorte"))
                .andExpect(jsonPath("$.logoUrl").value(org.hamcrest.Matchers.startsWith("https://signed.example/")))
                .andExpect(jsonPath("$.coverImageUrl").value(org.hamcrest.Matchers.startsWith("https://signed.example/")))
                .andExpect(jsonPath("$.branches").isArray())
                .andExpect(jsonPath("$.services").isArray());
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
