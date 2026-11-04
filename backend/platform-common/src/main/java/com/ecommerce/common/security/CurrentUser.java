package com.ecommerce.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the authenticated user's identity from a validated access token.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Access token subject is not a user ID", ex);
        }
    }

    public static String email(Jwt jwt) {
        return jwt.getClaimAsString(JwtClaimNames.EMAIL);
    }
}
