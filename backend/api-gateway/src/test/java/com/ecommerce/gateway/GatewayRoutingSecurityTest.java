package com.ecommerce.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Boots the real gateway against a stub downstream service to verify routing, header
 * propagation, JWT enforcement, CORS, and error mapping end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingSecurityTest {

    private static final String SECRET = "gateway-test-signing-key-0123456789-abc";
    private static final String ISSUER = "ecommerce-auth-service";
    private static final HttpServer DOWNSTREAM = startEchoServer();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        String downstream = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        registry.add("gateway.security.jwt-secret", () -> SECRET);
        registry.add("gateway.security.jwt-issuer", () -> ISSUER);
        registry.add("gateway.security.cors-allowed-origins", () -> "http://localhost:3000");
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> downstream);
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", () -> downstream);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri",
                () -> "http://localhost:" + closedPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void publicLoginRouteIsForwardedWithCorrelationId() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"x\"}")
                .header("X-Correlation-Id", "corr-login-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "corr-login-1")
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/auth/login")
                .jsonPath("$.correlationId").isEqualTo("corr-login-1")
                .jsonPath("$.authorization").isEqualTo("");
    }

    @Test
    void gatewayGeneratesCorrelationIdWhenMissing() {
        webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", id -> org.assertj.core.api.Assertions
                        .assertThat(id).matches("[0-9a-f-]{36}"))
                .expectBody()
                .jsonPath("$.correlationId").value(id -> org.assertj.core.api.Assertions
                        .assertThat((String) id).matches("[0-9a-f-]{36}"));
    }

    @Test
    void protectedRouteWithoutTokenIsRejectedAtTheEdge() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.path").isEqualTo("/api/orders");
    }

    @Test
    void validTokenIsForwardedUnchangedToTheService() {
        String token = token(List.of("CUSTOMER"), Instant.now().plus(Duration.ofMinutes(5)), SECRET);

        webTestClient.get().uri("/api/orders/17")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders/17")
                .jsonPath("$.authorization").isEqualTo("Bearer " + token);
    }

    @Test
    void expiredOrForgedTokensAreRejected() {
        String expired = token(List.of("CUSTOMER"), Instant.now().minus(Duration.ofMinutes(5)), SECRET);
        String forged = token(List.of("ADMIN"), Instant.now().plus(Duration.ofMinutes(5)),
                "attacker-controlled-key-0123456789-xyz");

        webTestClient.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void adminPathsRequireAdminRole() {
        String customer = token(List.of("CUSTOMER"), Instant.now().plus(Duration.ofMinutes(5)), SECRET);
        String admin = token(List.of("ADMIN", "CUSTOMER"), Instant.now().plus(Duration.ofMinutes(5)), SECRET);

        webTestClient.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        webTestClient.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/admin/users");
    }

    @Test
    void unavailableServiceReturns503Json() {
        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void unknownPathReturns404Json() {
        String token = token(List.of("CUSTOMER"), Instant.now().plus(Duration.ofMinutes(5)), SECRET);

        webTestClient.get().uri("/api/does-not-exist")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    void corsPreflightAllowsConfiguredOriginOnly() {
        webTestClient.options().uri("/api/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

        webTestClient.options().uri("/api/orders")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void healthEndpointIsPublic() {
        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }

    private static String token(List<String> roles, Instant expiresAt, String secret) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("42")
                .issuedAt(expiresAt.minus(Duration.ofMinutes(10)))
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static HttpServer startEchoServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String authorization = headerOrEmpty(exchange.getRequestHeaders().getFirst("Authorization"));
                String correlationId = headerOrEmpty(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
                byte[] body = ("{\"path\":\"" + exchange.getRequestURI().getPath()
                        + "\",\"authorization\":\"" + authorization
                        + "\",\"correlationId\":\"" + correlationId + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("X-Correlation-Id", correlationId);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start stub downstream server", ex);
        }
    }

    /** A port that was free a moment ago, so connections to it are refused. */
    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String headerOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
