package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import com.ecommerce.common.config.PlatformCommonAutoConfiguration;
import com.ecommerce.common.security.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final JwtProperties PROPERTIES = new JwtProperties(
            "unit-test-signing-key-0123456789-abcdef", "ecommerce-auth-service", Duration.ofMinutes(15));

    private final PlatformCommonAutoConfiguration common = new PlatformCommonAutoConfiguration();
    private final SecretKey key = common.jwtSigningKey(PROPERTIES);
    private final JwtDecoder decoder = common.jwtDecoder(key, PROPERTIES);

    @Test
    void issuedTokenValidatesWithResourceServerDecoder() {
        TokenService tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)), PROPERTIES, Clock.systemUTC());

        TokenService.IssuedToken token = tokenService.issueAccessToken(adminUser());
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ecommerce-auth-service");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("admin@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "CUSTOMER");
    }

    @Test
    void decoderRejectsTokenSignedWithDifferentKey() {
        JwtProperties otherKey = new JwtProperties(
                "another-signing-key-0123456789-abcdefgh", PROPERTIES.issuer(), PROPERTIES.accessTokenTtl());
        TokenService foreignIssuer = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(common.jwtSigningKey(otherKey))),
                otherKey, Clock.systemUTC());

        String forged = foreignIssuer.issueAccessToken(adminUser()).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        TokenService tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)), PROPERTIES, twoHoursAgo);

        String expired = tokenService.issueAccessToken(adminUser()).value();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    private static User adminUser() {
        User user = new User("admin@example.com", "hash", "Ada", "Admin", null);
        ReflectionTestUtils.setField(user, "id", 42L);
        user.addRole(new Role(RoleName.ADMIN));
        user.addRole(new Role(RoleName.CUSTOMER));
        return user;
    }
}
