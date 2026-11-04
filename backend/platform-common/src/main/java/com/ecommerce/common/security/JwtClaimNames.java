package com.ecommerce.common.security;

/**
 * Custom claims carried by platform access tokens. {@code sub} holds the numeric user ID.
 */
public final class JwtClaimNames {

    public static final String EMAIL = "email";
    public static final String ROLES = "roles";

    private JwtClaimNames() {
    }
}
