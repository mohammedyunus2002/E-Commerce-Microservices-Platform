package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TokenService tokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, tokenService);
    }

    @Test
    void registerStoresNormalizedEmailBcryptHashAndCustomerRole() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));
        when(tokenService.issueAccessToken(any())).thenReturn(new TokenService.IssuedToken("jwt", 900));

        AuthResponse response = authService.register(
                new RegisterRequest("  Jane@Example.COM ", "Secret123", "Jane", "Doe", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2").isNotEqualTo("Secret123");
        assertThat(passwordEncoder.matches("Secret123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().roleNames()).containsExactly(RoleName.CUSTOMER);

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "Secret123", "Jane", "Doe", null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("EMAIL_ALREADY_REGISTERED");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerMapsUniqueIndexRaceToConflict() {
        when(userRepository.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "Secret123", "Jane", "Doe", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = withId(new User("jane@example.com", passwordEncoder.encode("Secret123"), "Jane", "Doe", null), 3L);
        user.addRole(new Role(RoleName.CUSTOMER));
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user)).thenReturn(new TokenService.IssuedToken("jwt", 900));

        AuthResponse response = authService.login(new LoginRequest("JANE@example.com", "Secret123"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().email()).isEqualTo("jane@example.com");
    }

    @Test
    void loginRejectsWrongPasswordWithGenericMessage() {
        User user = withId(new User("jane@example.com", passwordEncoder.encode("Secret123"), "Jane", "Doe", null), 3L);
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "Wrong1234")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
                });
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test
    void loginRejectsUnknownEmailWithSameMessage() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "Secret123")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
                });
    }

    @Test
    void loginRejectsDisabledAccount() {
        User user = withId(new User("jane@example.com", passwordEncoder.encode("Secret123"), "Jane", "Doe", null), 3L);
        user.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "Secret123")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DISABLED"));
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
