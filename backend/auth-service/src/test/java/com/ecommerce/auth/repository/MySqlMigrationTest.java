package com.ecommerce.auth.repository;

import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the Flyway migrations to real MySQL 8.4 and validates the JPA mapping against it.
 * Skipped automatically when no Docker daemon is available.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void migrationsApplyAndEntitiesRoundTripOnMySql() {
        User user = new User("mysql@example.com", "$2a$04$hash", "My", "Sql", null);
        user.addRole(roleRepository.findByName(RoleName.CUSTOMER).orElseThrow());
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByEmailIgnoreCase("MYSQL@example.com")).isPresent();
    }
}
