package com.englishtranslation.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.limits.PrefillContext;
import com.englishtranslation.backend.prefill.PrefillItem;
import com.englishtranslation.backend.prefill.PrefillItemRepository;
import com.englishtranslation.backend.prefill.PrefillRunner;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** POST /api/admin/prefill/run and GET /api/admin/prefill: admin only, validation, counts and spend. */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.prefill.delay=0s"})
@AutoConfigureMockMvc
class AdminPrefillTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    PrefillItemRepository queue;
    @Autowired
    PrefillRunner runner;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    LoginRateLimiter loginLimiter;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void setUp() throws Exception {
        waitUntilIdle();
        users.deleteAll();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        queue.deleteAll();
        usageRows.deleteAll();
        loginLimiter.reset();
    }

    private void waitUntilIdle() throws InterruptedException {
        for (int i = 0; i < 100 && runner.isRunning(); i++) {
            Thread.sleep(50);
        }
    }

    private Cookie adminCookie() throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    @Test
    void bothCallsNeedAnAdminLogin() throws Exception {
        mockMvc.perform(get("/api/admin/prefill")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/prefill/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runRefusesAnUnsafeCapOrLimit() throws Exception {
        Cookie cookie = adminCookie();

        mockMvc.perform(post("/api/admin/prefill/run").param("maxUsd", "0").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/prefill/run").param("maxUsd", "50").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/prefill/run").param("limit", "0").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void runStartsInTheBackgroundAndAnswers202() throws Exception {
        Cookie cookie = adminCookie();

        mockMvc.perform(post("/api/admin/prefill/run").param("maxUsd", "0.02").param("limit", "5").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true))
                .andExpect(jsonPath("$.maxUsd").value(0.02))
                .andExpect(jsonPath("$.limit").value(5));
        waitUntilIdle();
    }

    @Test
    void overviewCountsByStatusAndSourceAndShowsPrefillSpend() throws Exception {
        queue.save(new PrefillItem("word", "lantern", "", "frequent-words", 0, Instant.now()));
        queue.save(new PrefillItem("word", "pebble", "", "frequent-words", 0, Instant.now()));
        PrefillItem done = new PrefillItem("sentence", "give up", "vi", "oewn-phrases", 0, Instant.now());
        done.finish(PrefillItem.DONE, null, Instant.now());
        queue.save(done);
        PrefillContext.runAs(new PrefillContext.Run(Instant.now(), BigDecimal.ONE),
                () -> usage.record(Feature.WORD, "claude-haiku-5-5", 1_000_000, 0)); // saved as PREFILL, $0.10

        mockMvc.perform(get("/api/admin/prefill").cookie(adminCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.byStatus.pending").value(2))
                .andExpect(jsonPath("$.byStatus.done").value(1))
                .andExpect(jsonPath("$.byStatus.failed").value(0))
                .andExpect(jsonPath("$.bySource.length()").value(2))
                .andExpect(jsonPath("$.bySource[?(@.source=='frequent-words')].byStatus.pending").value(2))
                .andExpect(jsonPath("$.bySource[?(@.source=='oewn-phrases')].byStatus.done").value(1))
                .andExpect(jsonPath("$.maxUsdPerRun").value(0.5))
                .andExpect(jsonPath("$.spentTodayUsd").value(0.1));

        // The admin usage page keeps working and splits the prefill spend out.
        mockMvc.perform(get("/api/admin/usage").param("days", "1").cookie(adminCookie()))
                .andExpect(jsonPath("$.todayUsd").value(0.0))
                .andExpect(jsonPath("$.prefillTodayUsd").value(0.1))
                .andExpect(jsonPath("$.days[0].byFeature.prefill.calls").value(1));
    }
}
