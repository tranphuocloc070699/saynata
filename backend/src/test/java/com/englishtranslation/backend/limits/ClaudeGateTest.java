package com.englishtranslation.backend.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.auth.AccessTokenService;
import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.SentenceTranslation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.translation.TargetLanguage;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The rules for a new Claude call, through the real HTTP layer with a fake Claude (TASK-66 to TASK-69):
 * guests get cache hits only, signed-in users may cause a call, a user over the limit gets 429, and a used-up
 * daily budget gives 503 with no Claude call. Here the sign-in rule is on (the old tests switch it off).
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true",
        "app.limits.claude-per-user-hour.max=2",
        "app.limits.public-read-per-ip.max=40"
})
@AutoConfigureMockMvc
class ClaudeGateTest {

    static final Explanation EXPLANATION = new Explanation("A greeting.", List.of(), "Xin chào");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SentenceTranslationRepository sentences;
    @Autowired
    WordLookupRepository words;
    @Autowired
    UserRepository users;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    AccessTokenService accessTokens;
    @Autowired
    ClaudeProperties properties;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    ClaudeService claude;

    String userToken;

    @BeforeEach
    void setUp() {
        sentences.deleteAll();
        words.deleteAll();
        usageRows.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
        User user = users.save(User.appUser("learner@example.com"));
        userToken = "Bearer " + accessTokens.createToken(user.getId());
    }

    // ---- helpers ----

    private void cacheSentence(String text) {
        String normalized = CacheKeys.normalizeSentence(text);
        String key = CacheKeys.key("sentence", properties.sentencePromptVersion(), "vi", normalized);
        sentences.save(new SentenceTranslation(key, normalized, "vi", "Cached meaning.", "[]", "Đã lưu",
                properties.model(), properties.sentencePromptVersion()));
    }

    private org.springframework.test.web.servlet.ResultActions translate(String text, String authorization) throws Exception {
        var request = post("/api/translations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions lookUp(String word, String authorization) throws Exception {
        var request = get("/api/words/" + word);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private String stream(String text, String authorization) throws Exception {
        var request = post("/api/translations/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        MvcResult started = mockMvc.perform(request).andExpect(request().asyncStarted()).andReturn();
        started.getAsyncResult(10_000);
        return mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private void spendTheWholeBudget() {
        // 10 million output tokens at $0.50 per million = $5, more than the $1 cap.
        usage.record(Feature.SENTENCE, "claude-haiku-5-5", 0, 10_000_000);
        assertThat(usage.budgetReached()).isTrue();
    }

    // ---- guests ----

    @Test
    void aGuestGetsACachedSentenceWithoutAnyClaudeCall() throws Exception {
        cacheSentence("Hello there");

        translate("Hello there", null).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));

        verifyNoInteractions(claude);
    }

    @Test
    void aGuestWhoNeedsANewSentenceGets401SignInRequired() throws Exception {
        translate("A brand new sentence", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("sign_in_required"))
                .andExpect(jsonPath("$.detail").value("Sign in to get a new explanation."));

        verifyNoInteractions(claude);
        assertThat(sentences.count()).isZero();
    }

    @Test
    void aBadTokenIsJustAGuest() throws Exception {
        translate("A brand new sentence", "Bearer garbage").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("sign_in_required"));
        cacheSentence("Hello there");
        translate("Hello there", "Bearer garbage").andExpect(status().isOk());
    }

    @Test
    void aGuestWhoNeedsANewWordGets401ButNonWordsAndCachedWordsStillWork() throws Exception {
        // "serendipity" is a real word, not bundled (no dictionary in this test) and not cached.
        lookUp("serendipity", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("sign_in_required"));
        // Not an English word: answered with "no senses" and no Claude call, so a guest may ask.
        lookUp("zzxqkv", null).andExpect(status().isOk());

        verifyNoInteractions(claude);
    }

    @Test
    void aGuestGetsACachedWord() throws Exception {
        when(claude.describeWord("serendipity")).thenReturn(new WordEntry(List.of()));
        lookUp("serendipity", userToken).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));

        lookUp("serendipity", null).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));

        verify(claude, times(1)).describeWord("serendipity");
    }

    @Test
    void theStreamSendsAnErrorEventWithTheSameCodeToAGuest() throws Exception {
        String body = stream("A brand new sentence", null);

        assertThat(body).contains("event:error")
                .contains("\"status\":401")
                .contains("\"code\":\"sign_in_required\"")
                .doesNotContain("event:done");
        verifyNoInteractions(claude);
    }

    @Test
    void aGuestStreamOfACachedSentenceIsFine() throws Exception {
        cacheSentence("Hello there");

        String body = stream("Hello there", null);

        assertThat(body).contains("event:done").doesNotContain("event:error");
        verifyNoInteractions(claude);
    }

    // ---- signed-in users ----

    @Test
    void aSignedInUserGetsANewSentenceFromClaude() throws Exception {
        when(claude.explainSentence("A brand new sentence")).thenReturn(EXPLANATION);

        translate("A brand new sentence", userToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        verify(claude).explainSentence("A brand new sentence");
    }

    @Test
    void aSignedInUserGetsANewWordFromClaude() throws Exception {
        when(claude.describeWord("serendipity")).thenReturn(new WordEntry(List.of()));

        lookUp("serendipity", userToken).andExpect(status().isOk());

        verify(claude).describeWord("serendipity");
    }

    @Test
    void theStreamKnowsTheUserEvenThoughItRunsOnAnotherThread() throws Exception {
        when(claude.explainSentenceStreaming(eq("A brand new sentence"), any()))
                .thenReturn(EXPLANATION);

        String body = stream("A brand new sentence", userToken);

        assertThat(body).contains("event:done").doesNotContain("event:error");
    }

    @Test
    void aUserOverTheirHourlyLimitGets429WithRetryAfterAndNoClaudeCall() throws Exception {
        // The limit is 2 per hour in this test class.
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);
        translate("First new sentence", userToken).andExpect(status().isOk());
        translate("Second new sentence", userToken).andExpect(status().isOk());

        translate("Third new sentence", userToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));

        verify(claude, times(2)).explainSentence(anyString());
    }

    @Test
    void cacheHitsDoNotUseUpTheUsersClaudeLimit() throws Exception {
        cacheSentence("Hello there");
        // The first request saves the language line (one small call); the next ones are free hits.
        when(claude.translateLines(any(), anyList())).thenReturn(List.of("Xin chào"));
        for (int i = 0; i < 6; i++) {
            translate("Hello there", userToken).andExpect(status().isOk());
        }
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);

        translate("A brand new sentence", userToken).andExpect(status().isOk());
    }

    @Test
    void theStreamReportsRateLimitedInTheErrorEvent() throws Exception {
        when(claude.explainSentenceStreaming(anyString(), any())).thenReturn(EXPLANATION);
        stream("First new sentence", userToken);
        stream("Second new sentence", userToken);

        String body = stream("Third new sentence", userToken);

        assertThat(body).contains("event:error").contains("\"status\":429").contains("\"code\":\"rate_limited\"");
    }

    // ---- the daily budget ----

    @Test
    void whenTheBudgetIsUsedUpANewSentenceGets503AndClaudeIsNotCalled() throws Exception {
        spendTheWholeBudget();

        translate("A brand new sentence", userToken)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("daily_limit_reached"))
                .andExpect(jsonPath("$.detail").value("Saynata has reached today's limit for new explanations. "
                        + "Cached words still work. Try again tomorrow."));

        verifyNoInteractions(claude);
    }

    @Test
    void whenTheBudgetIsUsedUpANewWordGets503AndClaudeIsNotCalled() throws Exception {
        spendTheWholeBudget();

        lookUp("serendipity", userToken).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("daily_limit_reached"));

        verify(claude, never()).describeWord(anyString());
    }

    @Test
    void whenTheBudgetIsUsedUpTheStreamSendsAnErrorEventAndClaudeIsNotCalled() throws Exception {
        spendTheWholeBudget();

        String body = stream("A brand new sentence", userToken);

        assertThat(body).contains("event:error").contains("\"status\":503").contains("\"code\":\"daily_limit_reached\"");
        verifyNoInteractions(claude);
    }

    @Test
    void whenTheBudgetIsUsedUpCachedAnswersStillWorkForEveryone() throws Exception {
        cacheSentence("Hello there");
        spendTheWholeBudget();

        translate("Hello there", null).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        translate("Hello there", userToken).andExpect(status().isOk());
        lookUp("zzxqkv", null).andExpect(status().isOk());
    }

    @Test
    void aCallStillGoesThroughWhenTheSpendIsJustBelowTheCap() throws Exception {
        // Spend just below the cap: a call still goes through.
        usage.record(Feature.WORD, "claude-haiku-5-5", 0, 1_000_000); // $0.50
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);

        translate("A brand new sentence", userToken).andExpect(status().isOk());
    }

    // ---- the public read limit ----

    @Test
    void tooManyPublicReadsFromOneIpGet429WithRetryAfter() throws Exception {
        cacheSentence("Hello there");
        // The limit is 40 per minute in this test class (the real default is 120).
        for (int i = 0; i < 40; i++) {
            translate("Hello there", null).andExpect(status().isOk());
        }

        translate("Hello there", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
        lookUp("zzxqkv", null).andExpect(status().isTooManyRequests());
        // Another IP is fine.
        mockMvc.perform(get("/api/words/zzxqkv").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        })).andExpect(status().isOk());
        // The health check is not limited.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
