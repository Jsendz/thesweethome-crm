package com.sweethomecompany.crm.user;

/**
 * Every field is optional (nullable, not primitive): omitting a field means
 * "leave it unchanged." Before this, {@code active} was a primitive
 * boolean, so an empty body or a body that only set another field silently
 * defaulted it to false and deactivated the user.
 */
public record UpdateUserRequest(Boolean active, String fullName, Role role, String password) {
}
