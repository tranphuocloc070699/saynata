package com.englishtranslation.backend.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.translation.SentenceTranslation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * TASK-83.4: cachedOnly lookups for the app while the user types. Sign-in is required here, the strictest case:
 * a guest still gets cached and bundled answers, and a miss is 404 not_cached (never 401). ClaudeGate and Claude
 * are mocks that must never be touched.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true"
})
@AutoConfigureMockMvc
class CachedOnlyTest {

    static final String ENTRY = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"B2\",\"simpleDefinition\":\"a lucky chance\","
            + "\"vietnamese\":\"may mắn\",\"examples\":[],\"usage\":[]}]}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SentenceTranslationRepository sentences;
    /** The current sentence prompt version, so a prompt bump does not break these tests. */
    @Value("${app.claude.sentence-prompt-version}")
    String sentencePromptVersion;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    ClaudeGate gate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from word_lookup_media");
        lookups.deleteAll();
        bundled.deleteAll();
        sentences.deleteAll();
        rateLimiter.reset();
    }

    private void saveBundledRun() {
        DictionaryWord word = new DictionaryWord("run", 5, "/rʌn/");
        DictionarySense sense = new DictionarySense(word, 0);
        sense.setText("verb", "A1", "to move fast on your legs", "chạy", "[]", "[]");
        word.getSenses().add(sense);
        bundled.save(word);
    }

    private void saveCachedWord(String word) {
        lookups.save(new WordLookup(CacheKeys.key("word", "v7", word), word, ENTRY, "m", "v7"));
    }

    private void saveSentence(String text) {
        sentences.save(new SentenceTranslation(CacheKeys.key("sentence", sentencePromptVersion, "vi", text), text, "vi",
                "A greeting.", "[]", "Xin chào", "m", sentencePromptVersion));
    }

    private ResultActions word(String word) throws Exception {
        return mockMvc.perform(get("/api/words/" + word).param("cachedOnly", "true"));
    }

    private ResultActions sentence(String text) throws Exception {
        return mockMvc.perform(post("/api/translations").param("cachedOnly", "true")
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}"));
    }

    private String stream(String text) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/translations/stream").param("cachedOnly", "true")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"text\": \"" + text + "\", \"targetLang\": \"vi\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        started.getAsyncResult(10_000);
        return mockMvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void aBundledWordIsAnsweredAndTheAppMayKeepItADay() throws Exception {
        saveBundledRun();

        word("run").andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses[0].simpleDefinition").value("to move fast on your legs"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));

        verifyNoInteractions(claude, gate);
    }

    @Test
    void aCachedWordIsAnsweredAndTheAppMayKeepItAnHour() throws Exception {
        saveCachedWord("serendipity");

        word("serendipity").andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"));
        // The same header without cachedOnly.
        mockMvc.perform(get("/api/words/serendipity"))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"));

        verifyNoInteractions(claude, gate);
    }

    @Test
    void aWordMissIs404NotCachedForAGuestAndNeverReachesTheGate() throws Exception {
        word("serendipity").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_cached"))
                .andExpect(header().string("Cache-Control", "no-store"));

        verifyNoInteractions(claude, gate);
        assertThat(lookups.count()).isZero();
    }

    @Test
    void aWordThatIsNotEnglishIsStillTheEmptyAnswerAndIsNotKept() throws Exception {
        // Spring Security's default "no-cache, no-store" stays: the word list may change on the next deploy.
        word("zzqxv").andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses").isEmpty())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

        verifyNoInteractions(claude, gate);
    }

    @Test
    void aCachedSentenceIsAnsweredAlsoByTheLooseMatch() throws Exception {
        saveSentence("How are you?");

        sentence("how are you").andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.explanation.meaning").value("A greeting."));
        sentence("HOW ARE YOU\u2026").andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.text").value("HOW ARE YOU\u2026"));

        verifyNoInteractions(claude, gate);
    }

    @Test
    void aSentenceMissIs404NotCachedAndNothingIsSaved() throws Exception {
        sentence("A brand new sentence").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_cached"));
        // Personal text is never cached, so it is always a miss here.
        sentence("Write to pat@example.com").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_cached"));

        verifyNoInteractions(claude, gate);
        assertThat(sentences.count()).isZero();
    }

    @Test
    void theStreamSendsDoneOnAHitAndANotCachedErrorOnAMiss() throws Exception {
        saveSentence("Good morning");

        assertThat(stream("Good morning")).contains("event:done").doesNotContain("event:error");
        // The loose match works in the stream too.
        assertThat(stream("good morning!")).contains("event:done").doesNotContain("event:error");
        assertThat(stream("A brand new sentence"))
                .contains("event:error").contains("\"status\":404").contains("\"code\":\"not_cached\"");

        verifyNoInteractions(claude, gate);
    }
}
