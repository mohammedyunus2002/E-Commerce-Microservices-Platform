package com.ecommerce.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Applies the settings every service shares: stateless sessions, bearer-token authentication,
 * JSON error responses, and open health, metrics, and API-doc endpoints. Each service still
 * declares its own path rules after calling {@link #apply(HttpSecurity)}.
 */
public class StatelessJwtSecurity {

    public static final String[] INFRASTRUCTURE_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final JsonSecurityErrorHandlers errorHandlers;

    public StatelessJwtSecurity(JwtAuthenticationConverter jwtAuthenticationConverter,
                                JsonSecurityErrorHandlers errorHandlers) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.errorHandlers = errorHandlers;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                // Tokens travel in the Authorization header, never in cookies, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers));
    }
}
