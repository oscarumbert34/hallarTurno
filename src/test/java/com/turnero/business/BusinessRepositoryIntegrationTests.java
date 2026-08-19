package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import com.turnero.user.UserStatus;
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
class BusinessRepositoryIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero")
            .withUsername("turnero")
            .withPassword("turnero");

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void duplicateSlugViolatesUniqueConstraint() {
        User owner = User.create("business-constraint@example.com", "hash", UserRole.BUSINESS);
        owner.updateStatus(UserStatus.ACTIVE);
        User savedOwner = userRepository.saveAndFlush(owner);

        businessRepository.saveAndFlush(Business.create(
                savedOwner,
                "Nombre",
                null,
                null,
                null,
                "nombre",
                BusinessStatus.ACTIVE
        ));

        assertThatThrownBy(() -> businessRepository.saveAndFlush(Business.create(
                savedOwner,
                "Otro nombre",
                null,
                null,
                null,
                "nombre",
                BusinessStatus.ACTIVE
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
