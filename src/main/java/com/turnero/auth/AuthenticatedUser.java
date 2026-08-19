package com.turnero.auth;

import com.turnero.user.UserRole;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Set<UserRole> roles) {
}
