package com.englishtranslation.backend.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * REQUIRE_SIGN_IN=false (until the app with sign-in is released): guests may cause new Claude calls, but those calls
 * still go through ClaudeGate. The daily budget stops them, and a per-IP limit (2 per hour in this test) stops
 * one guest from using the whole budget alone.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=false",
        "app.limits.claude-per-guest-ip.max=2"
})
@AutoConfigureMockMvc
class GuestModeTest {

    static final Explanation EXPLANATION = new Explanation("A greeting.", List.of(), "Xin chào");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SentenceTranslationRepository sentences;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    ClaudeUsageService usage;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void setUp() {
        sentences.deleteAll();
        usageRows.deleteAll();
        rateLimiter.reset();
        when(claude.explainSentence(anyString())).thenReturn(EXPLANATION);
        when(claude.explainSentenceStreaming(anyString(), any())).thenReturn(EXPLANATION);
    }

    private ResultActions guestTranslate(String text, String ip) throws Exception {
        return mockMvc.perform(post("/api/translations").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}"));
    }

    @Test
    void aGuestMayCauseANewClaudeCallWhenSignInIsNotRequired() throws Exception {
        guestTranslate("A brand new sentence", "203.0.113.1").andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false));

        verify(claude).explainSentence(anyString());
    }

    @Test
    void aGuestIpIsLimitedSoOneGuestCannotUseTheWholeBudget() throws Exception {
        guestTranslate("First new sentence", "203.0.113.1").andExpect(status().isOk());
        guestTranslate("Second new sentence", "203.0.113.1").andExpect(status().isOk());

        guestTranslate("Third new sentence", "203.0.113.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));

        verify(claude, times(2)).explainSentence(anyString());
        // Another guest IP is not affected, and a cache hit never counts.
        guestTranslate("Third new sentence", "203.0.113.2").andExpect(status().isOk());
        guestTranslate("First new sentence", "203.0.113.1").andExpect(status().isOk());
    }

    @Test
    void theStreamOfAGuestIsLimitedToo() throws Exception {
        for (int i = 0; i < 3; i++) {
            MvcResult started = mockMvc.perform(post("/api/translations/stream").with(r -> {
                r.setRemoteAddr("203.0.113.5");
                return r;
            }).contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                    .content("{\"text\": \"Stream sentence " + i + "\", \"targetLang\": \"vi\"}"))
                    .andExpect(request().asyncStarted()).andReturn();
            started.getAsyncResult(10_000);
            String body = mockMvc.perform(asyncDispatch(started)).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            if (i < 2) {
                assertThat(body).doesNotContain("event:error");
            } else {
                assertThat(body).contains("\"status\":429").contains("\"code\":\"rate_limited\"");
            }
        }
    }

    @Test
    void whenTheBudgetIsUsedUpAGuestGets503AndClaudeIsNotCalled() throws Exception {
        usage.record(Feature.SENTENCE, "claude-haiku-5-5", 0, 10_000_000); // $5, above the $1 cap

        guestTranslate("A brand new sentence", "203.0.113.1")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("daily_limit_reached"));

        verifyNoInteractions(claude);
    }
}
