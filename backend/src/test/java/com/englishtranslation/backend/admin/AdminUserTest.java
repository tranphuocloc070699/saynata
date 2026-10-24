package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.auth.AccessTokenService;
import com.englishtranslation.backend.auth.AuthService;
import com.englishtranslation.backend.auth.AuthTestSupport;
import com.englishtranslation.backend.auth.RefreshTokenRepository;
import com.englishtranslation.backend.auth.SocialProvider;
import com.englishtranslation.backend.auth.UserIdentity;
import com.englishtranslation.backend.auth.UserIdentityRepository;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsage;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.limits.CallerContext;
import com.englishtranslation.backend.liked.LikedItem;
import com.englishtranslation.backend.liked.LikedItemRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The admin Users page API (TASK-105): list, usage, block (checked in ClaudeGate), unblock and delete. */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.auth.require-sign-in=true"})
@AutoConfigureMockMvc
class AdminUserTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    LoginRateLimiter loginLimiter;
    @Autowired
    UserIdentityRepository identities;
    @Autowired
    LikedItemRepository liked;
    @Autowired
    RefreshTokenRepository refreshTokens;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    WordLookupRepository words;
    @Autowired
    AccessTokenService accessTokens;
    @Autowired
    AuthService auth;
    @MockitoBean
    ClaudeService claude;

    Cookie adminCookie;
    User admin;
    User linh;
    User minh;

    @BeforeEach
    void setUp() throws Exception {
        liked.deleteAll();
        identities.deleteAll();
        refreshTokens.deleteAll();
        usageRows.deleteAll();
        words.deleteAll();
        users.deleteAll();
        loginLimiter.reset();
        admin = users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin",
                Role.ADMIN));
        linh = users.save(User.appUser("linh@example.com"));
        minh = users.save(User.appUser("minh@example.com"));
        identities.save(new UserIdentity(linh.getId(), SocialProvider.GOOGLE, "g-1", "linh@example.com",
                Instant.now()));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(adminCookie).contentType(MediaType.APPLICATION_JSON);
    }

    private void usageOf(User user, Feature feature, Instant at, String cost) {
        usageRows.save(new ClaudeUsage(at, "haiku", feature, 10, 10, new BigDecimal(cost), user.getId()));
    }

    @Test
    void everyCallNeedsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        String userToken = accessTokens.createToken(linh.getId());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listShowsProvidersUsageAndATotal() throws Exception {
        Instant now = Instant.now();
        usageOf(linh, Feature.WORD, now, "0.01");
        usageOf(linh, Feature.SENTENCE, now.minus(3, ChronoUnit.DAYS), "0.02");
        usageOf(linh, Feature.ADMIN, now, "0.50"); // costs money, but is not an explanation

        mockMvc.perform(asAdmin(get("/api/admin/users?_start=0&_end=50&_sort=email&_order=ASC")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$[0].admin").value(true))
                .andExpect(jsonPath("$[1].email").value("linh@example.com"))
                .andExpect(jsonPath("$[1].providers[0]").value("google"))
                .andExpect(jsonPath("$[1].explanationsToday").value(1))
                .andExpect(jsonPath("$[1].explanationsTotal").value(2))
                .andExpect(jsonPath("$[1].costTotalUsd").value(0.53))
                .andExpect(jsonPath("$[2].providers.length()").value(0));
    }

    @Test
    void listFiltersBySearchBlockedAdminAndProvider() throws Exception {
        minh.block("spam", Instant.now());
        users.save(minh);

        mockMvc.perform(asAdmin(get("/api/admin/users?q=LIN"))).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].email").value("linh@example.com"));
        mockMvc.perform(asAdmin(get("/api/admin/users?blocked=true"))).andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].blocked").value(true));
        mockMvc.perform(asAdmin(get("/api/admin/users?admin=true"))).andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(asAdmin(get("/api/admin/users?provider=google")))
                .andExpect(header().string("X-Total-Count", "1"));
        // "email" = no Google or Apple sign-in.
        mockMvc.perform(asAdmin(get("/api/admin/users?provider=email")))
                .andExpect(header().string("X-Total-Count", "2"));
        mockMvc.perform(asAdmin(get("/api/admin/users?provider=facebook"))).andExpect(status().isBadRequest());
    }

    @Test
    void detailHasUsageByPeriodTheLast14DaysAndTheAccount() throws Exception {
        Instant now = Instant.now();
        usageOf(linh, Feature.WORD, now, "0.01");
        usageOf(linh, Feature.WORD, now.minus(2, ChronoUnit.DAYS), "0.01");
        usageOf(linh, Feature.SENTENCE, now.minus(30, ChronoUnit.DAYS), "0.04");
        liked.save(new LikedItem(linh.getId(), "word", "run", "run", "to move fast", now));
        AuthTestSupport.signIn(auth, linh); // a sign-in: one device

        mockMvc.perform(asAdmin(get("/api/admin/users/" + linh.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("linh@example.com"))
                .andExpect(jsonPath("$.today.calls").value(1))
                .andExpect(jsonPath("$.last7Days.calls").value(2))
                .andExpect(jsonPath("$.total.calls").value(3))
                .andExpect(jsonPath("$.total.costUsd").value(0.06))
                .andExpect(jsonPath("$.wordsTotal").value(2))
                .andExpect(jsonPath("$.sentencesTotal").value(1))
                .andExpect(jsonPath("$.days.length()").value(14))
                .andExpect(jsonPath("$.days[13].calls").value(1))
                .andExpect(jsonPath("$.identities[0].provider").value("google"))
                .andExpect(jsonPath("$.likedCount").value(1))
                .andExpect(jsonPath("$.devicesSignedIn").value(1));
        mockMvc.perform(asAdmin(get("/api/admin/users/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void summaryCountsAccountsActiveUsersAndTodaysUsage() throws Exception {
        usageOf(linh, Feature.WORD, Instant.now(), "0.01");
        usageRows.save(new ClaudeUsage(Instant.now(), "haiku", Feature.PREFILL, 1, 1, new BigDecimal("1.00"), null));
        AuthTestSupport.signIn(auth, linh);

        mockMvc.perform(asAdmin(get("/api/admin/users/summary")))
                .andExpect(jsonPath("$.accounts").value(3))
                .andExpect(jsonPath("$.activeToday").value(1))
                .andExpect(jsonPath("$.explanationsToday").value(1))
                .andExpect(jsonPath("$.costTodayUsd").value(0.01));
    }

    @Test
    void aBlockedUserGetsNoNewClaudeCallButKeepsCachedAnswers() throws Exception {
        String token = accessTokens.createToken(linh.getId());
        when(claude.describeWord("serendipity")).thenReturn(new WordEntry(List.of()));
        mockMvc.perform(get("/api/words/serendipity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(post("/api/admin/users/" + linh.getId() + "/block"))
                        .content("{\"reason\": \"980 calls in one day\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.blocked").value(true))
                .andExpect(jsonPath("$.blockedReason").value("980 calls in one day"));

        // A new word needs Claude: refused with a code the app can read.
        mockMvc.perform(get("/api/words/serenity").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_blocked"));
        // The cached word still works.
        mockMvc.perform(get("/api/words/serendipity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(claude).describeWord("serendipity");

        mockMvc.perform(asAdmin(post("/api/admin/users/" + linh.getId() + "/unblock")))
                .andExpect(jsonPath("$.user.blocked").value(false))
                .andExpect(jsonPath("$.blockedReason").doesNotExist());
        assertThat(users.findById(linh.getId()).orElseThrow().isBlocked()).isFalse();
    }

    @Test
    void anAdminCannotBeBlockedOrDeletedHere() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/users/" + admin.getId() + "/block"))).andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(delete("/api/admin/users/" + admin.getId()))).andExpect(status().isBadRequest());
        assertThat(users.findById(admin.getId())).isPresent();
        mockMvc.perform(asAdmin(post("/api/admin/users/" + linh.getId() + "/block"))
                .content("{\"reason\": \"" + "x".repeat(301) + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesTheAccountItsSignInsTokensAndLikesButKeepsUsage() throws Exception {
        Instant now = Instant.now();
        liked.save(new LikedItem(linh.getId(), "word", "run", "run", "to move fast", now));
        usageOf(linh, Feature.WORD, now, "0.01");
        AuthTestSupport.signIn(auth, linh);

        mockMvc.perform(asAdmin(delete("/api/admin/users/" + linh.getId()))).andExpect(status().isNoContent());

        assertThat(users.findById(linh.getId())).isEmpty();
        assertThat(identities.findAll()).isEmpty();
        assertThat(liked.countByUserId(linh.getId())).isZero();
        assertThat(refreshTokens.findAll()).isEmpty();
        assertThat(usageRows.findAll()).hasSize(1); // cost history stays
        mockMvc.perform(asAdmin(delete("/api/admin/users/" + linh.getId()))).andExpect(status().isNotFound());
    }

    @Test
    void usageIsSavedWithTheCallersUserAndLastSeenAtMostOnceAnHour() {
        CallerContext.runAs(new CallerContext.Caller(minh.getId(), "1.2.3.4"),
                () -> usage.record(Feature.WORD, "haiku", 10, 10));
        assertThat(usageRows.findAll()).singleElement().extracting(ClaudeUsage::getUserId).isEqualTo(minh.getId());

        AuthTestSupport.signIn(auth, minh);
        Instant first = users.findById(minh.getId()).orElseThrow().getLastSeenAt();
        assertThat(first).isNotNull();
        AuthTestSupport.signIn(auth, minh);
        assertThat(users.findById(minh.getId()).orElseThrow().getLastSeenAt()).isEqualTo(first);
        verifyNoInteractions(claude);
    }
}
