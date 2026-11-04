package com.ecommerce.auth.service;

import java.util.Locale;

final class EmailNormalizer {

    private EmailNormalizer() {
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
