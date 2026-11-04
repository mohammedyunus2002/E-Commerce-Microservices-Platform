package com.ecommerce.auth.repository;

import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations against H2 in MySQL mode and checks Hibernate's
 * {@code ddl-auto=validate} accepts them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seedMigrationCreatesBothRoles() {
        assertThat(roleRepository.findAll())
                .extracting("name")
                .containsExactlyInAnyOrder(RoleName.CUSTOMER, RoleName.ADMIN);
    }

    @Test
    void persistsUserWithRolesAndFindsByEmailIgnoringCase() {
        User user = new User("jane@example.com", "$2a$04$hash", "Jane", "Doe", "+15550100");
        user.addRole(roleRepository.findByName(RoleName.CUSTOMER).orElseThrow());
        userRepository.saveAndFlush(user);

        User found = userRepository.findByEmailIgnoreCase("JANE@EXAMPLE.COM").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.roleNames()).containsExactly(RoleName.CUSTOMER);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(userRepository.existsByEmailIgnoreCase("jane@example.com")).isTrue();
    }

    @Test
    void uniqueIndexRejectsDuplicateEmail() {
        userRepository.saveAndFlush(new User("dup@example.com", "h", "A", "B", null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("dup@example.com", "h", "C", "D", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
