package com.turnero.auth;

import com.turnero.business.Business;
import com.turnero.business.BusinessRepository;
import com.turnero.common.ApiException;
import com.turnero.user.User;
import com.turnero.user.UserRepository;
import com.turnero.user.UserRole;
import com.turnero.user.UserStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UserRole role = parsePublicRole(request.role());
        User user = User.create(email, passwordEncoder.encode(request.password()), role);
        user.updateStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.saveAndFlush(user);
        String accessToken = jwtService.generateAccessToken(savedUser);

        return AuthResponse.bearer(
                UserResponse.from(savedUser),
                findPrimaryBusinessId(savedUser),
                accessToken,
                jwtService.accessTokenExpiresInSeconds()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByNormalizedEmail(request.email())
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User is not active");
        }

        String accessToken = jwtService.generateAccessToken(user);
        return AuthResponse.bearer(
                UserResponse.from(user),
                findPrimaryBusinessId(user),
                accessToken,
                jwtService.accessTokenExpiresInSeconds()
        );
    }

    private UUID findPrimaryBusinessId(User user) {
        if (user.getId() == null) {
            return null;
        }
        return businessRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .findFirst()
                .map(Business::getId)
                .orElse(null);
    }

    private UserRole parsePublicRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.CUSTOMER;
        }
        if (UserRole.CUSTOMER.name().equals(role) || UserRole.BUSINESS.name().equals(role)) {
            return UserRole.valueOf(role);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Role is not allowed for public registration");
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
