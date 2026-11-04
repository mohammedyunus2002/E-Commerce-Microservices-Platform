package com.ecommerce.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Optional first admin account, read from {@code BOOTSTRAP_ADMIN_EMAIL} and
 * {@code BOOTSTRAP_ADMIN_PASSWORD}. Nothing is created when either value is blank.
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(String email, String password) {

    public boolean isConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}
