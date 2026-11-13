package com.sweethomecompany.crm.auth;

import com.sweethomecompany.crm.user.Role;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String fullName, Role role) {
}
