package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * At start-up: if there is no ADMIN yet, create the first one from ADMIN_EMAIL and ADMIN_INITIAL_PASSWORD.
 * Once an admin exists this does nothing, so changing the env values later changes nothing.
 */
@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsWithRole(Role.ADMIN)) {
            return;
        }
        String email = properties.email();
        String password = properties.initialPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_INITIAL_PASSWORD to create the first one.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_INITIAL_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        users.save(new User(normalizedEmail, passwordEncoder.encode(password), "Admin", Role.ADMIN));
        // Never log the password.
        log.info("Created the first admin account for {}.", normalizedEmail);
    }
}
