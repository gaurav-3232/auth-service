package com.authservice.config;

import com.authservice.domain.entity.Role;
import com.authservice.domain.entity.UserEntity;
import com.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-time admin user bootstrap.
 * Set ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD env vars to create
 * the first admin user on startup. If the email already exists, it's skipped.
 * Remove the env vars after first use for security.
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AdminBootstrapProperties properties,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.bootstrapEmail();
        String password = properties.bootstrapPassword();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.debug("Admin bootstrap skipped: ADMIN_BOOTSTRAP_EMAIL/PASSWORD not set");
            return;
        }

        if (userRepository.existsByEmail(email.toLowerCase().trim())) {
            log.info("Admin bootstrap skipped: user {} already exists", email);
            return;
        }

        UserEntity admin = new UserEntity(
                email.toLowerCase().trim(),
                passwordEncoder.encode(password),
                "Admin",
                Role.ADMIN
        );
        userRepository.save(admin);
        log.info("Admin user bootstrapped: {}", email);
    }
}
