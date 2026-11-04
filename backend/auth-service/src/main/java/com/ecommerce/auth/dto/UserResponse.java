package com.ecommerce.auth.dto;

import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Set<RoleName> roles,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                new TreeSet<>(user.roleNames()),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
