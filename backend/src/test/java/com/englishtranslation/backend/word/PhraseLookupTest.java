package com.englishtranslation.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.ClaudeGate;
import com.englishtranslation.backend.limits.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Phrases ("give up", "a piece of cake") from the local collection (TASK-83.9) are answered only from word_lookup:
 * never Claude, never "not a word". Sign-in is required here, so a guest proves that no new call can happen.
 */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true"
})
@AutoConfigureMockMvc
class PhraseLookupTest {

    static final String ENTRY = "{\"senses\":[{\"pos\":\"phrasal verb\",\"cefr\":\"A2\","
            + "\"simpleDefinition\":\"to stop trying to do something\",\"vietnamese\":\"bỏ cuộc\","
            + "\"examples\":[],\"usage\":[]}]}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    ClaudeProperties properties;
    @Autowired
    IpaDictionary ipaDictionary;
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
        rateLimiter.reset();
    }

    private void saveCachedPhrase(String phrase) {
        lookups.save(new WordLookup(CacheKeys.key("word", properties.wordPromptVersion(), phrase), phrase, ENTRY,
                "claude-haiku-5-5", properties.wordPromptVersion()));
    }

    private ResultActions phrase(String text, boolean cachedOnly) throws Exception {
        return mockMvc.perform(get("/api/words/{word}", text).param("cachedOnly", String.valueOf(cachedOnly)));
    }

    @Test
    void aCachedPhraseIsAWordCardWithTheIpaOfItsWords() throws Exception {
        saveCachedPhrase("give up");

        for (boolean cachedOnly : new boolean[] {true, false}) {
            phrase("Give  up", cachedOnly).andExpect(status().isOk())
                    .andExpect(jsonPath("$.word").value("give up"))
                    .andExpect(jsonPath("$.cached").value(true))
                    .andExpect(jsonPath("$.ipa").value("/ˈɡɪv ˈəp/"))
                    .andExpect(jsonPath("$.entry.senses[0].pos").value("phrasal verb"))
                    .andExpect(header().string("Cache-Control", "max-age=3600, public"));
        }

        verifyNoInteractions(claude, gate);
    }

    @Test
    void aPhraseMissIs404NotCachedEvenWithoutCachedOnly() throws Exception {
        // "not a word" would be wrong here: the app must fall back to the sentence explanation.
        phrase("look forward to", false).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_cached"));
        phrase("look forward to", true).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_cached"));

        verifyNoInteractions(claude, gate);
        assertThat(lookups.count()).isZero();
    }

    @Test
    void aPhraseWithAWordMissingFromTheIpaListHasNoIpa() {
        assertThat(ipaDictionary.ipaFor("give zzqxv")).isNull();
        assertThat(ipaDictionary.ipaFor("Give")).isEqualTo("/ˈɡɪv/");
    }

    @Test
    void moreThanSixWordsOrMarksAreRejected() throws Exception {
        phrase("one two three four five six seven", true).andExpect(status().isBadRequest());
        phrase("give up!", true).andExpect(status().isBadRequest());

        verifyNoInteractions(claude, gate);
    }
}
