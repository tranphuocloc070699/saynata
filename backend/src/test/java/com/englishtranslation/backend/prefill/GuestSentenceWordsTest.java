package com.englishtranslation.backend.prefill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.word.WordLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A guest's new sentence is refused (sign_in_required), but its unknown words still go to the prefill queue, so the
 * night job answers them (TASK-100). The test database has no bundled words, so every English word is unknown.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true"
})
@AutoConfigureMockMvc
class GuestSentenceWordsTest {

    static final String BODY = "{\"text\": \"The lantern glows.\", \"targetLang\": \"vi\"}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PrefillItemRepository queue;
    @Autowired
    WordLookupRepository words;
    @Autowired
    SentenceTranslationRepository sentences;
    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void clean() {
        queue.deleteAll();
        words.deleteAll();
        sentences.deleteAll();
    }

    /** The queue is filled by another thread, so wait for it (at most 5 seconds). */
    private void waitUntilQueued(String text) throws InterruptedException {
        for (int i = 0; i < 100 && queue.findByKindAndTextAndTargetLang("word", text, "").isEmpty(); i++) {
            Thread.sleep(50);
        }
    }

    @Test
    void aRefusedGuestSentenceStillQueuesItsWords() throws Exception {
        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        waitUntilQueued("lantern");
        waitUntilQueued("glows");

        PrefillItem lantern = queue.findByKindAndTextAndTargetLang("word", "lantern", "").orElseThrow();
        assertThat(lantern.getSource()).isEqualTo(SentenceWordQueue.SOURCE);
        assertThat(lantern.getStatus()).isEqualTo("pending");
        verifyNoInteractions(claude);
    }

    @Test
    void theStreamingEndpointQueuesTheWordsOfARefusedSentenceToo() throws Exception {
        var started = mockMvc.perform(post("/api/translations/stream").contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andReturn();
        mockMvc.perform(asyncDispatch(started));
        waitUntilQueued("lantern");

        assertThat(queue.findByKindAndTextAndTargetLang("word", "lantern", "")).isPresent();
    }

    @Test
    void aCachedOnlyMissQueuesNothing() throws Exception {
        mockMvc.perform(post("/api/translations?cachedOnly=true").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        Thread.sleep(300); // the worker would have had time to queue something

        assertThat(queue.findAll()).isEmpty();
    }

    @Test
    void aSentenceRejectedForAnotherReasonQueuesNothing() throws Exception {
        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"   \", \"targetLang\": \"vi\"}"))
                .andExpect(status().isBadRequest());
        Thread.sleep(300);

        assertThat(queue.findAll()).isEmpty();
    }
}
