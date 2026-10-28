package com.saynata.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saynata.backend.TestcontainersConfiguration;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: the whole app (filter, controller, validation, service, JPA, Flyway) on a real Postgres.
 * Nothing is mocked. Skipped (not failed) when Docker is not running.
 *
 * Do NOT add @Transactional to this class: the test would wrap everything in one transaction
 * and hide the race condition we want to see.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// The real URL and password come from the Testcontainers container. These only keep the
// ${DB_URL} placeholders in application.yml resolvable on a machine without backend/.env (CI).
@TestPropertySource(properties = {"DB_URL=unused", "DB_USER=unused", "DB_PASSWORD=unused"})
class WordApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    WordRepository words;

    @BeforeEach
    void cleanTable() {
        words.deleteAll();
    }

    private static String json(String headword) {
        return """
                {"headword":"%s","partOfSpeech":"verb","simpleExplanation":"to move fast on foot"}"""
                .formatted(headword);
    }

    private MvcResult create(String headword) throws Exception {
        return mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(json(headword)))
                .andReturn();
    }

    @Test
    void createThenRead_worksEndToEnd() throws Exception {
        MvcResult created = mvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Run")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/words/")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        String location = created.getResponse().getHeader("Location");

        // Read it back by the Location header, then by headword in another case.
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headword").value("Run"));
        mvc.perform(get("/api/words").param("headword", "RUN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headword").value("Run"));

        assertThat(words.count()).isEqualTo(1);
    }

    @Test
    void duplicateWord_inAnotherCase_returns409AndKeepsOneRow() throws Exception {
        create("Run");

        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(json("run")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("word_already_exists"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(words.count()).isEqualTo(1);
    }

    @Test
    void invalidBody_returns400AndSavesNothing() throws Exception {
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(json("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("headword"));

        assertThat(words.count()).isZero();
    }

    @Test
    void unknownWord_returns404() throws Exception {
        mvc.perform(get("/api/words").param("headword", "nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("word_not_found"));
    }

    /**
     * The race condition: many users add the same new word at the same moment.
     * Both layers can say no: the "exists?" check in the service (409 word_already_exists), or,
     * when two requests pass that check together, the unique index (409 conflict).
     * Whatever the timing, the result must be one 201, all others 409, and exactly one row.
     */
    @Test
    void sameWordAtTheSameMoment_savesExactlyOneRow() throws Exception {
        int callers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            String headword = i % 2 == 0 ? "Serendipity" : "serendipity"; // different case, same word
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(); // every thread waits here, then all start together
                return create(headword);
            }));
        }
        ready.await();
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        List<String> conflictCodes = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(30, TimeUnit.SECONDS);
            int status = result.getResponse().getStatus();
            statuses.add(status);
            if (status == 409) {
                conflictCodes.add(JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
            }
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(callers - 1);
        assertThat(conflictCodes).allMatch(code -> code.equals("word_already_exists") || code.equals("conflict"));
        assertThat(words.count()).isEqualTo(1);
    }
}
