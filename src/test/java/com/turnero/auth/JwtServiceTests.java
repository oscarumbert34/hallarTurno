package com.turnero.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.user.User;
import com.turnero.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTests {

    private static final String SECRET = "test-secret-key-with-at-least-thirty-two-characters";

    @Test
    void parsesValidToken() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        JwtService jwtService = jwtService(now, Duration.ofMinutes(15));
        User user = user();

        AuthenticatedUser authenticatedUser = jwtService.parse(jwtService.generateAccessToken(user));

        assertThat(authenticatedUser.id()).isEqualTo(user.getId());
        assertThat(authenticatedUser.email()).isEqualTo(user.getEmail());
        assertThat(authenticatedUser.roles()).containsExactly(UserRole.CUSTOMER);
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String token = jwtService(now, Duration.ofMinutes(15)).generateAccessToken(user());

        assertThatThrownBy(() -> jwtService(now.plus(Duration.ofMinutes(16)), Duration.ofMinutes(15)).parse(token))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Expired token");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService(Instant.parse("2026-01-01T00:00:00Z"), Duration.ofMinutes(15))
                .generateAccessToken(user());

        assertThatThrownBy(() -> jwtService(Instant.parse("2026-01-01T00:00:00Z"), Duration.ofMinutes(15))
                .parse(token.substring(0, token.length() - 2) + "xx"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid token");
    }

    private JwtService jwtService(Instant instant, Duration expiration) {
        return new JwtService(
                new JwtProperties(SECRET, expiration),
                new ObjectMapper(),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private User user() {
        User user = User.create("user@example.com", "hash", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", UUID.fromString("4eb7444b-b321-43f1-9c50-885f80f7463a"));
        return user;
    }
}
