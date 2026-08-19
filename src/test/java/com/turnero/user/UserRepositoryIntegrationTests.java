package com.turnero.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class UserRepositoryIntegrationTests {

    private static final String PASSWORD_HASH = "$2a$10$7QJ8r3P2h5WcP9qH6n6n3eGq9RvXkV5b5w8K8YwzXw7JrK8YwzXw7";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero")
            .withUsername("turnero")
            .withPassword("turnero");

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void normalizesEmailBeforePersisting() {
        User user = userRepository.saveAndFlush(User.create("  USER@Example.COM  ", PASSWORD_HASH, UserRole.CUSTOMER));

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(userRepository.findByNormalizedEmail("USER@example.COM"))
                .map(User::getId)
                .contains(user.getId());
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        userRepository.saveAndFlush(User.create("owner@example.com", PASSWORD_HASH, UserRole.BUSINESS));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.create(" OWNER@EXAMPLE.COM ", PASSWORD_HASH, UserRole.CUSTOMER)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNullStatus() {
        User user = User.create("pending@example.com", PASSWORD_HASH, UserRole.CUSTOMER);
        user.updateStatus(null);

        assertThatThrownBy(() -> userRepository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void doesNotExposePasswordHashInJsonOrToString() throws Exception {
        User user = User.create("admin@example.com", PASSWORD_HASH, UserRole.ADMIN);

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash", PASSWORD_HASH);
        assertThat(user.toString()).doesNotContain("passwordHash", PASSWORD_HASH);
    }
}
