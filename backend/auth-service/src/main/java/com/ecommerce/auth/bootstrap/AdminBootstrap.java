package com.ecommerce.auth.bootstrap;

import com.ecommerce.auth.domain.RoleName;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Creates the first ADMIN account on startup when bootstrap credentials are provided. Existing
 * accounts are never modified, so restarting with different values does not reset a password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public AdminBootstrap(BootstrapAdminProperties properties,
                          UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("No bootstrap admin configured; skipping");
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Bootstrap admin account already exists; leaving it unchanged");
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                User admin = new User(email, passwordEncoder.encode(properties.password()),
                        "Platform", "Admin", null);
                admin.addRole(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
                admin.addRole(roleRepository.findByName(RoleName.CUSTOMER).orElseThrow());
                userRepository.saveAndFlush(admin);
                log.info("Created bootstrap admin account {}", admin.getId());
            });
        } catch (DataIntegrityViolationException ex) {
            // Another replica created it first.
            log.info("Bootstrap admin was created concurrently by another instance");
        }
    }
}
