package com.ecommerce.product.service;

import com.ecommerce.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.Locale;

final class Slugs {

    private static final int MAX_LENGTH = 120;

    private Slugs() {
    }

    /** "Home &amp; Kitchen" becomes "home-kitchen"; accents are folded to ASCII. */
    static String of(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_NAME",
                    "Category name must contain at least one letter or digit");
        }
        return slug.length() > MAX_LENGTH ? slug.substring(0, MAX_LENGTH) : slug;
    }
}
