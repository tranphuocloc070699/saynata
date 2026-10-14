package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Login, cookie, 401 without login, CORS, and the public endpoints staying open. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminAuthTest {

    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LoginRateLimiter rateLimiter;

    @Autowired
    AdminTokenService tokens;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        users.save(new User(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "Admin", Role.ADMIN));
        rateLimiter.reset();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")).andReturn();
    }

    @Test
    void adminEndpointsReturn401WithoutLogin() throws Exception {
        mockMvc.perform(get("/api/admin/words")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/sentences")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginSetsAnHttpOnlySecureStrictCookieAndMeWorks() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("saynata_admin=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Max-Age=28800");

        Cookie cookie = result.getResponse().getCookie("saynata_admin");
        mockMvc.perform(get("/api/admin/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.displayName").value("Admin"));
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        assertThat(login("Admin@Example.com", ADMIN_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        MvcResult wrongPassword = login(ADMIN_EMAIL, "wrong-password");
        MvcResult unknownEmail = login("nobody@example.com", ADMIN_PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString())
                .contains("Wrong email or password.");
        assertThat(wrongPassword.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void aUserWithoutTheAdminRoleCannotLogIn() throws Exception {
        users.save(new User("reader@example.com", passwordEncoder.encode(ADMIN_PASSWORD), "Reader", Role.USER));

        assertThat(login("reader@example.com", ADMIN_PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aTokenOfADeletedUserIsRejected() throws Exception {
        Cookie cookie = login(ADMIN_EMAIL, ADMIN_PASSWORD).getResponse().getCookie("saynata_admin");
        users.deleteAll();

        mockMvc.perform(get("/api/admin/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void aMadeUpTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/words").cookie(new Cookie("saynata_admin", "not.a.token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() throws Exception {
        AdminProperties other = new AdminProperties(true, "another-secret-another-secret-123456789", "", "", true,
                java.util.List.of());
        String forged = new AdminTokenService(other).createToken(users.findByEmail(ADMIN_EMAIL).orElseThrow().getId());

        mockMvc.perform(get("/api/admin/words").cookie(new Cookie("saynata_admin", forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void loginIsRateLimitedTo10AttemptsPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(login(ADMIN_EMAIL, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        // The 11th attempt is refused, even with the right password.
        assertThat(login(ADMIN_EMAIL, ADMIN_PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void writesWithoutJsonContentTypeAreRefused() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void corsAllowsTheAdminSiteWithCredentialsAndExposesTotalCount() throws Exception {
        mockMvc.perform(options("/api/admin/words")
                        .header("Origin", "https://admin.loffy.me")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.loffy.me"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(get("/api/admin/words").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Total-Count"));
    }

    @Test
    void corsRefusesOtherSites() throws Exception {
        mockMvc.perform(options("/api/admin/words")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpointsStayOpenWithoutLogin() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // "zzzzzzzz" is not an English word: answered with no senses and no Claude call.
        mockMvc.perform(get("/api/words/zzzzzzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses").isEmpty());
        mockMvc.perform(post("/api/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest()); // reaches the controller (400 from validation), not 401
    }

    @Test
    void publicEndpointsDoNotSendCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/words/zzzzzzzz").header("Origin", "https://admin.loffy.me"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
