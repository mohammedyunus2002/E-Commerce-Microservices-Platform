package com.ecommerce.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void rejectsSecretShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "issuer", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void defaultsTokenLifetimeWhenUnset() {
        JwtProperties properties = new JwtProperties("x".repeat(32), "issuer", null);

        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(60));
    }
}
