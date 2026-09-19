package com.turnero.auth;

import java.util.UUID;

public record AuthResponse(
        UserResponse user,
        UUID businessId,
        String businessSlug,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    static AuthResponse bearer(
            UserResponse user,
            UUID businessId,
            String businessSlug,
            String accessToken,
            long expiresInSeconds
    ) {
        return new AuthResponse(user, businessId, businessSlug, accessToken, "Bearer", expiresInSeconds);
    }
}
