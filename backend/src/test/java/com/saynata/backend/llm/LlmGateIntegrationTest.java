package com.saynata.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saynata.backend.TestcontainersConfiguration;
import com.saynata.backend.word.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Small limits so the tests stay short: 1 call costs 0.001, the day allows 4 calls, one IP may start 2 per hour.
 * The LLM is a mock, so "never called" is something we can prove.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "DB_URL=unused", "DB_USER=unused", "DB_PASSWORD=unused",
        "app.llm.cost-per-call-usd=0.001",
        "app.llm.daily-budget-usd=0.004",
        "app.llm.max-calls-per-ip-per-hour=2"})
class LlmGateIntegrationTest {

    private static final WordDraft DRAFT = new WordDraft("noun", null, "a simple explanation");

    @Autowired
    MockMvc mvc;

    @Autowired
    WordRepository words;

    @Autowired
    LlmUsageRepository usage;

    @MockitoBean
    LlmClient llm;

    @BeforeEach
    void clean() {
        words.deleteAll();
        usage.deleteAll();
        when(llm.explain(anyString())).thenReturn(DRAFT);
    }

    private ResultActions lookup(String word, String ip) throws Exception {
        return mvc.perform(get("/api/lookup").param("q", word).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }));
    }

    @Test
    void overTheIpLimit_returns429_theLlmIsNotCalled_otherIpsAreFine() throws Exception {
        lookup("apple", "10.0.0.1").andExpect(status().isOk());
        lookup("bread", "10.0.0.1").andExpect(status().isOk());

        lookup("cloud", "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        verify(llm, never()).explain("cloud");
        assertThat(words.count()).isEqualTo(2);

        lookup("cloud", "10.0.0.2").andExpect(status().isOk());
        // A word we already know does not use the limit.
        lookup("apple", "10.0.0.1").andExpect(status().isOk()).andExpect(jsonPath("$.source").value("cache"));
    }

    @Test
    void overTheDailyBudget_returns503_theLlmIsNotCalled_knownWordsStillWork() throws Exception {
        lookup("apple", "10.0.0.1").andExpect(status().isOk());
        lookup("bread", "10.0.0.1").andExpect(status().isOk());
        lookup("cloud", "10.0.0.2").andExpect(status().isOk());
        lookup("dream", "10.0.0.2").andExpect(status().isOk()); // 4 x 0.001 = the whole budget

        lookup("eagle", "10.0.0.3")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("budget_exhausted"));
        verify(llm, never()).explain("eagle");
        verify(llm, times(4)).explain(anyString());

        lookup("apple", "10.0.0.3").andExpect(status().isOk()).andExpect(jsonPath("$.source").value("cache"));
    }

    @Test
    void junkIsRejectedBeforeAnyPayment() throws Exception {
        lookup("abc123", "10.0.0.1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("not_a_word"));
        lookup("select * from word", "10.0.0.1").andExpect(status().isBadRequest());

        verify(llm, never()).explain(anyString());
        assertThat(usage.count()).isZero();
    }

    @Test
    void aFailedLlmCallIsNotCountedAsUsage() throws Exception {
        when(llm.explain("apple")).thenThrow(new IllegalStateException("provider down"));

        lookup("apple", "10.0.0.1").andExpect(status().isBadGateway());

        assertThat(usage.count()).isZero();
    }
}
