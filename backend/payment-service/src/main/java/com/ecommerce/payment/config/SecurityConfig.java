package com.ecommerce.payment.config;

import com.ecommerce.common.security.StatelessJwtSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, StatelessJwtSecurity statelessJwtSecurity)
            throws Exception {
        return statelessJwtSecurity.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(StatelessJwtSecurity.INFRASTRUCTURE_PATHS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .build();
    }
}
