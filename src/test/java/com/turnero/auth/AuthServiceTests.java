package com.turnero.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import com.turnero.user.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTests {

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final BusinessRepository businessRepository = org.mockito.Mockito.mock(BusinessRepository.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final JwtService jwtService = org.mockito.Mockito.mock(JwtService.class);
    private final AuthService authService = new AuthService(userRepository, businessRepository, passwordEncoder, jwtService);

    @Test
    void registerUsesCustomerRoleByDefaultAndHashesPassword() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("token");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(businessRepository.findByOwnerIdOrderByCreatedAtDesc(any())).thenReturn(java.util.List.of());

        AuthResponse response = authService.register(new RegisterRequest(" USER@example.COM ", "password123", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(captor.getValue().getRoles()).containsExactly(UserRole.CUSTOMER);
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.accessToken()).isEqualTo("token");
    }

    @Test
    void registerRejectsAdminRole() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("admin@example.com", "password123", "ADMIN")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Role is not allowed for public registration");
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = User.create("user@example.com", "bcrypt-hash", UserRole.CUSTOMER);
        user.updateStatus(UserStatus.ACTIVE);
        when(userRepository.findByNormalizedEmail("USER@example.COM")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("USER@example.COM", "wrong-password")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid credentials");
    }
}
