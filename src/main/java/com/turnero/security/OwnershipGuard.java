package com.turnero.security;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.business.Business;
import com.turnero.common.ApiException;
import com.turnero.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {

    public void requireBusinessOrAdmin(AuthenticatedUser currentUser) {
        if (currentUser.roles().contains(UserRole.BUSINESS) || isAdmin(currentUser)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Only business users or admins can manage businesses");
    }

    public void requireOwnerOrAdmin(Business business, AuthenticatedUser currentUser, String message) {
        if (business.getOwner().getId().equals(currentUser.id()) || isAdmin(currentUser)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public boolean isAdmin(AuthenticatedUser currentUser) {
        return currentUser.roles().contains(UserRole.ADMIN);
    }
}
