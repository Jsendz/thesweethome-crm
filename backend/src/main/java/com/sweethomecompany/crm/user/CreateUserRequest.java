package com.sweethomecompany.crm.user;

public record CreateUserRequest(String email, String password, String fullName, Role role) {
}
