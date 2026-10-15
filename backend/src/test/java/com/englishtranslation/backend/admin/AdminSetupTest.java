package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.englishtranslation.backend.claude.ClaudeService;

/** First admin from env, and the fail-fast check for the JWT secret. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
class AdminSetupTest {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    private AdminProperties properties(String email, String password) {
        return new AdminProperties(true, "x".repeat(40), email, password, true, List.of());
    }

    @Test
    void createsTheFirstAdminWhenNoneExists() {
        new AdminBootstrap(users, passwordEncoder, properties("First@Example.com", "a-long-initial-password"))
                .run(new DefaultApplicationArguments());

        User admin = users.findByEmail("first@example.com").orElseThrow();
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo("a-long-initial-password");
        assertThat(passwordEncoder.matches("a-long-initial-password", admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        users.save(new User("old@example.com", passwordEncoder.encode("whatever-password"), "Old", Role.ADMIN));

        new AdminBootstrap(users, passwordEncoder, properties("new@example.com", "a-long-initial-password"))
                .run(new DefaultApplicationArguments());

        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByEmail("new@example.com")).isEmpty();
    }

    @Test
    void doesNothingWhenTheEnvValuesAreEmpty() {
        new AdminBootstrap(users, passwordEncoder, properties("", "")).run(new DefaultApplicationArguments());

        assertThat(users.count()).isZero();
    }

    @Test
    void refusesAShortInitialPassword() {
        AdminBootstrap bootstrap = new AdminBootstrap(users, passwordEncoder, properties("a@example.com", "short"));

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12");
        assertThat(users.count()).isZero();
    }

    @Test
    void missingOrShortJwtSecretFailsFast() {
        assertThatThrownBy(() -> new AdminTokenService(new AdminProperties(true, "", "", "", true, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_JWT_SECRET");
        assertThatThrownBy(() -> new AdminTokenService(new AdminProperties(true, "too-short", "", "", true, List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTokenWorksAndAChangedTokenDoesNot() {
        AdminTokenService service = new AdminTokenService(properties("", ""));
        String token = service.createToken(7);

        assertThat(service.readUserId(token)).isEqualTo(7);
        assertThat(service.readUserId(token + "x")).isEqualTo(-1);
    }
}
