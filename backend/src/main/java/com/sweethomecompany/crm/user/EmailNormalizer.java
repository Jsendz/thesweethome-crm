package com.sweethomecompany.crm.user;

/**
 * Every email address this app stores or looks up goes through here first.
 * Without it, "Maria@sweethome.ad" (typed at creation) and
 * "maria@sweethome.ad" (typed at login) are different strings as far as
 * the database and Spring Security's AppUserDetailsService are concerned -
 * the real person fails to log in with their own email, and the admin can
 * create two accounts that a human would call duplicates. The database
 * also enforces this at the row level (migration V2, a unique index on
 * lower(email)) as a backstop for any path that forgets to call this.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
