package com.englishtranslation.backend.admin;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.englishtranslation.backend.word.WordEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    static final WordEntry ENTRY = new WordEntry(List.of(new WordEntry.Sense(
            "noun", "B1", "a thing", "một thứ", List.of(), List.of())));

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
    void runWithSourceParamsAnswersOnlyThoseSourcesAndWithoutAnswersAll() throws Exception {
        when(claude.describeWord(anyString())).thenReturn(ENTRY);
        Cookie cookie = adminCookie();
        queue.save(new PrefillItem("word", "lantern", "", "scowl-70", 0, Instant.now()));
        queue.save(new PrefillItem("word", "pebble", "", "common-sentences", 0, Instant.now()));
        queue.save(new PrefillItem("word", "hesitant", "", "oewn-2025", 0, Instant.now()));

        mockMvc.perform(post("/api/admin/prefill/run").param("source", "scowl-70").param("source", "oewn-2025")
                .cookie(cookie).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0]").value("scowl-70"));
        waitUntilIdle();
        org.assertj.core.api.Assertions.assertThat(statusOf("lantern")).isEqualTo("done");
        org.assertj.core.api.Assertions.assertThat(statusOf("hesitant")).isEqualTo("done");
        org.assertj.core.api.Assertions.assertThat(statusOf("pebble")).isEqualTo("pending");

        mockMvc.perform(post("/api/admin/prefill/run").cookie(cookie).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sources.length()").value(0));
        waitUntilIdle();
        org.assertj.core.api.Assertions.assertThat(statusOf("pebble")).isEqualTo("done");
    }

    private String statusOf(String text) {
        return queue.findAll().stream().filter(i -> i.getText().equals(text)).findFirst().orElseThrow().getStatus();
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

    @Test
    void itemsListsTheQueueWithFiltersAndATotal() throws Exception {
        Cookie cookie = adminCookie();
        queue.save(new PrefillItem("word", "lantern", "", "sentence-words", 300, Instant.now()));
        queue.save(new PrefillItem("word", "pebble", "", "scowl-70", 0, Instant.now()));
        PrefillItem done = new PrefillItem("word", "giraffe", "", "sentence-words", 300, Instant.now());
        done.finish("done", null, Instant.now());
        queue.save(done);
        queue.save(new PrefillItem("sentence", "looks good.", "vi", "common-sentences", 0, Instant.now()));

        mockMvc.perform(get("/api/admin/prefill/items?kind=word&status=pending&_start=0&_end=50").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("pebble")) // newest first
                .andExpect(jsonPath("$[1].text").value("lantern"))
                .andExpect(jsonPath("$[1].source").value("sentence-words"))
                .andExpect(jsonPath("$[1].seenCount").value(1));

        mockMvc.perform(get("/api/admin/prefill/items?kind=word&status=pending&q=LAN").cookie(cookie))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].text").value("lantern"));
        // _end=1 is how the admin reads a count without the rows.
        mockMvc.perform(get("/api/admin/prefill/items?status=pending&_start=0&_end=1").cookie(cookie))
                .andExpect(header().string("X-Total-Count", "3"));
    }

    @Test
    void itemsRefusesAnUnknownKindOrStatusAndNeedsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/prefill/items")).andExpect(status().isUnauthorized());
        Cookie cookie = adminCookie();
        mockMvc.perform(get("/api/admin/prefill/items?kind=phrase").cookie(cookie)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/prefill/items?status=waiting").cookie(cookie)).andExpect(status().isBadRequest());
    }

    @Test
    void deleteTakesARowOutOfTheQueueAndKeepsItSkipped() throws Exception {
        // Every admin write needs a JSON Content-Type (the backend refuses others, a CSRF guard); the web app sends it.
        Cookie cookie = adminCookie();
        PrefillItem lantern = queue.save(new PrefillItem("word", "lantern", "", "sentence-words", 300, Instant.now()));

        mockMvc.perform(delete("/api/admin/prefill/items/" + lantern.getId()).cookie(cookie).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("skipped"));

        PrefillItem saved = queue.findById(lantern.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("skipped");
        assertThat(saved.getLastError()).isEqualTo("Removed by the admin");
        mockMvc.perform(get("/api/admin/prefill/items?status=pending").cookie(cookie))
                .andExpect(header().string("X-Total-Count", "0"));
        mockMvc.perform(delete("/api/admin/prefill/items/999999").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/prefill/items/" + lantern.getId())
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
