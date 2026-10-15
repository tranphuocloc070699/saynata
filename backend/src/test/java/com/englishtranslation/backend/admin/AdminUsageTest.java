package com.englishtranslation.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
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

/** GET /api/admin/usage: admin only, spend per day and feature. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminUsageTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    LoginRateLimiter loginLimiter;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        usageRows.deleteAll();
        loginLimiter.reset();
    }

    private Cookie adminCookie() throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    @Test
    void needsAnAdminLogin() throws Exception {
        mockMvc.perform(get("/api/admin/usage")).andExpect(status().isUnauthorized());
    }

    @Test
    void showsTodaysSpendByFeature() throws Exception {
        usage.record(Feature.SENTENCE, "claude-haiku-5-5", 1_000_000, 0); // $0.10
        usage.record(Feature.WORD, "claude-haiku-5-5", 0, 1_000_000); // $0.50

        mockMvc.perform(get("/api/admin/usage").param("days", "3").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyBudgetUsd").value(1.0))
                .andExpect(jsonPath("$.todayUsd").value(0.6))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].total.calls").value(2))
                .andExpect(jsonPath("$.days[0].byFeature.sentence.costUsd").value(0.1))
                .andExpect(jsonPath("$.days[0].byFeature.word.outputTokens").value(1_000_000))
                .andExpect(jsonPath("$.days[1].total.calls").value(0));
    }

    @Test
    void daysDefaultsToSevenAndIsCapped() throws Exception {
        Cookie cookie = adminCookie();

        mockMvc.perform(get("/api/admin/usage").cookie(cookie)).andExpect(jsonPath("$.days.length()").value(7));
        mockMvc.perform(get("/api/admin/usage").param("days", "5000").cookie(cookie))
                .andExpect(jsonPath("$.days.length()").value(90));
    }
}
