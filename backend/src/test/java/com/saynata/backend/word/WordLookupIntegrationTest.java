package com.saynata.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saynata.backend.TestcontainersConfiguration;
import com.saynata.backend.llm.LlmClient;
import com.saynata.backend.llm.LlmUsageRepository;
import com.saynata.backend.llm.WordDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real app + real Postgres. Only the LLM is a mock, so we can count how many times we "pay".
 * No @Transactional (see WordApiIntegrationTest).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"DB_URL=unused", "DB_USER=unused", "DB_PASSWORD=unused"})
class WordLookupIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    WordRepository words;

    @Autowired
    LlmUsageRepository usage;

    @MockitoBean
    LlmClient llm; // reset by Spring after every test

    @BeforeEach
    void cleanTable() {
        words.deleteAll();
        usage.deleteAll();
    }

    private static final WordDraft DRAFT = new WordDraft("noun", "/ˌserənˈdɪpɪti/", "a lucky discovery by accident");

    @Test
    void missCallsTheLlmOnce_thenEveryOtherSpellingComesFromTheCache() throws Exception {
        when(llm.explain("serendipity")).thenReturn(DRAFT);

        mvc.perform(get("/api/lookup").param("q", "Serendipity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("llm"))
                .andExpect(jsonPath("$.word.headword").value("serendipity"));
        mvc.perform(get("/api/lookup").param("q", "  SERENDIPITY "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("cache"));

        verify(llm, times(1)).explain(anyString());
        assertThat(words.count()).isEqualTo(1);

        mvc.perform(get("/api/lookup").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_query"));
    }

    /** The money test: 20 users type the same new word at the same moment. We must pay once. */
    @Test
    void sameNewWordAtTheSameMoment_callsTheLlmOnce() throws Exception {
        when(llm.explain("serendipity")).thenAnswer(call -> {
            Thread.sleep(500); // a slow LLM, so all the callers really overlap
            return DRAFT;
        });

        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            String q = i % 2 == 0 ? "Serendipity" : " serendipity ";
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mvc.perform(get("/api/lookup").param("q", q)).andReturn().getResponse().getStatus();
            }));
        }
        ready.await();
        go.countDown();

        for (Future<Integer> future : futures) {
            assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();

        verify(llm, times(1)).explain(anyString());
        assertThat(words.count()).isEqualTo(1);
        assertThat(usage.count()).isEqualTo(1); // 20 callers, one payment
    }

    @Test
    void badLlmAnswers_returnBadGatewayAndSaveNothing_thenTheNextTryWorks() throws Exception {
        when(llm.explain("serendipity"))
                .thenThrow(new IllegalStateException("provider down"))
                .thenReturn(new WordDraft("noun", null, "   ")) // empty explanation
                .thenReturn(DRAFT);

        mvc.perform(get("/api/lookup").param("q", "serendipity"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("llm_failed"));
        mvc.perform(get("/api/lookup").param("q", "serendipity"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("llm_bad_answer"));
        assertThat(words.count()).isZero();

        mvc.perform(get("/api/lookup").param("q", "serendipity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("llm"));
        assertThat(words.count()).isEqualTo(1);
    }
}
