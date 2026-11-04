package com.ecommerce.auth.dto;

import com.ecommerce.auth.domain.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<RoleName> roles) {
}
