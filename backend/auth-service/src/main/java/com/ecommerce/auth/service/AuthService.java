package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown, so both failure paths cost one BCrypt check. */
    private final String unknownUserHash;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        user.addRole(roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is missing; check Flyway migrations")));

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations passed the exists check; the unique index decides.
            throw emailTaken();
        }

        log.info("Registered user {}", user.getId());
        return authenticated(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> candidate = userRepository.findByEmailIgnoreCase(EmailNormalizer.normalize(request.email()));
        String hash = candidate.map(User::getPasswordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (candidate.isEmpty() || !passwordMatches) {
            log.warn("Rejected login attempt{}", candidate.map(u -> " for user " + u.getId()).orElse(""));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }

        User user = candidate.get();
        if (!user.isEnabled()) {
            log.warn("Rejected login for disabled user {}", user.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
        }

        log.info("User {} logged in", user.getId());
        return authenticated(user);
    }

    private AuthResponse authenticated(User user) {
        TokenService.IssuedToken token = tokenService.issueAccessToken(user);
        return AuthResponse.bearer(token.value(), token.expiresInSeconds(), UserResponse.from(user));
    }

    private static ConflictException emailTaken() {
        return new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
    }
}
