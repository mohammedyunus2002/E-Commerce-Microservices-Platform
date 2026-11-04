package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.UpdateProfileRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Profile self-service for the signed-in user and account administration for admins.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                              RoleRepository roleRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        user.updateProfile(
                request.firstName().trim(),
                request.lastName().trim(),
                StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD",
                    "Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("User {} changed their password", userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(long actingAdminId, long userId, Set<RoleName> roleNames) {
        if (actingAdminId == userId && !roleNames.contains(RoleName.ADMIN)) {
            throw new ConflictException("SELF_DEMOTION_FORBIDDEN", "Admins cannot remove their own ADMIN role");
        }
        User user = findUser(userId);
        List<Role> roles = roleRepository.findByNameIn(roleNames);
        if (roles.size() != roleNames.size()) {
            throw new IllegalStateException("Role table is missing one of " + roleNames);
        }
        user.replaceRoles(new HashSet<>(roles));
        log.info("Admin {} set roles of user {} to {}", actingAdminId, userId, roleNames);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(long actingAdminId, long userId, boolean enabled) {
        if (actingAdminId == userId && !enabled) {
            throw new ConflictException("SELF_DISABLE_FORBIDDEN", "Admins cannot disable their own account");
        }
        User user = findUser(userId);
        user.setEnabled(enabled);
        log.info("Admin {} {} user {}", actingAdminId, enabled ? "enabled" : "disabled", userId);
        return UserResponse.from(user);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
