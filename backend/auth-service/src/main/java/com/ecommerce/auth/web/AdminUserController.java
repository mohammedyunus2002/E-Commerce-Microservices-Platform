package com.ecommerce.auth.web;

import com.ecommerce.auth.dto.UpdateRolesRequest;
import com.ecommerce.auth.dto.UpdateUserStatusRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.service.UserAccountService;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "User administration")
public class AdminUserController {

    private final UserAccountService userAccountService;

    public AdminUserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    @Operation(summary = "List users, newest first")
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userAccountService.listUsers(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get one user")
    public UserResponse get(@PathVariable long userId) {
        return userAccountService.getUser(userId);
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "Replace a user's roles")
    public UserResponse updateRoles(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable long userId,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userAccountService.updateRoles(CurrentUser.id(jwt), userId, request.roles());
    }

    @PutMapping("/{userId}/status")
    @Operation(summary = "Enable or disable a user's login. Existing tokens stay valid until they expire.")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable long userId,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return userAccountService.setEnabled(CurrentUser.id(jwt), userId, request.enabled());
    }
}
