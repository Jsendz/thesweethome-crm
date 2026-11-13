package com.sweethomecompany.crm.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Role role, boolean active, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.isActive(),
            user.getCreatedAt());
    }
}
