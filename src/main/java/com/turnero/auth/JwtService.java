package com.turnero.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.user.User;
import com.turnero.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        if (!StringUtils.hasText(properties.secret()) || properties.secret().length() < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 characters");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("roles", user.getRoles().stream().map(Enum::name).sorted().toList());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = encode(header);
        String encodedPayload = encode(payload);
        return encodedHeader + "." + encodedPayload + "." + sign(encodedHeader + "." + encodedPayload);
    }

    public AuthenticatedUser parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BadCredentialsException("Invalid token");
        }

        String signedContent = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(signedContent), parts[2])) {
            throw new BadCredentialsException("Invalid token");
        }

        Map<String, Object> payload = decodePayload(parts[1]);
        long expiresAt = ((Number) payload.get("exp")).longValue();
        if (Instant.now(clock).getEpochSecond() >= expiresAt) {
            throw new BadCredentialsException("Expired token");
        }

        UUID id = UUID.fromString((String) payload.get("sub"));
        String email = (String) payload.get("email");
        @SuppressWarnings("unchecked")
        Set<UserRole> roles = ((List<String>) payload.get("roles")).stream()
                .map(UserRole::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(id, email, roles);
    }

    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpiration().toSeconds();
    }

    private String encode(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode JWT", exception);
        }
    }

    private Map<String, Object> decodePayload(String payload) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            return objectMapper.readValue(decoded, MAP_TYPE);
        } catch (Exception exception) {
            throw new BadCredentialsException("Invalid token", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
