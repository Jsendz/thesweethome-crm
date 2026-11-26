package com.sweethomecompany.crm.user;

import java.util.UUID;

public record UserDirectoryEntry(UUID id, String fullName) {

    public static UserDirectoryEntry from(AppUser user) {
        return new UserDirectoryEntry(user.getId(), user.getFullName());
    }
}
