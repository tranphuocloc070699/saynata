package com.englishtranslation.backend.suggest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.limits.RateLimiter;
import com.englishtranslation.backend.translation.SentenceTranslation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.util.List;
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

/** TASK-83.4: GET /api/suggest, prefix suggestions from bundled words, cached words and cached phrases. */
@SpringBootTest(properties = {
        "app.claude.api-key=test-key",
        "app.auth.require-sign-in=true",
        "app.limits.public-read-per-ip.max=5"
})
@AutoConfigureMockMvc
class SuggestTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    SuggestService service;
    @Autowired
    SentenceTranslationRepository sentences;
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

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from word_lookup_media");
        lookups.deleteAll();
        bundled.deleteAll();
        sentences.deleteAll();
        rateLimiter.reset();
    }

    private void bundledWord(String lemma, int rank) {
        bundled.save(new DictionaryWord(lemma, rank, null));
    }

    private void cachedWord(String word, String promptVersion) {
        lookups.save(new WordLookup(CacheKeys.key("word", promptVersion, word), word, "{\"senses\":[]}", "m",
                promptVersion));
    }

    private void phrase(String text, String lang) {
        sentences.save(new SentenceTranslation(CacheKeys.key("sentence", "v6", lang, text), text, lang,
                "m", "[]", "t", "m", "v6"));
    }

    private List<String> texts(String q, String lang) {
        return service.suggest(q, lang).suggestions().stream().map(Suggestion::text).toList();
    }

    @Test
    void wordsComeFirstExactMatchFirstThenByRankThenCachedWordsThenPhrases() {
        bundledWord("apply", 300);
        bundledWord("apple", 900);
        bundledWord("app", 2000);
        cachedWord("appetite", "v7");
        cachedWord("appendix", "v1"); // an old prompt version is not a cache hit, so not a suggestion
        cachedWord("banana", "v7");
        phrase("Apple pie is my favourite", "vi");

        assertThat(texts(" APP ", null))
                .containsExactly("app", "apply", "apple", "appetite", "Apple pie is my favourite");
        List<Suggestion> suggestions = service.suggest("app", null).suggestions();
        assertThat(suggestions.get(0).kind()).isEqualTo("word");
        assertThat(suggestions.get(4).kind()).isEqualTo("phrase");
    }

    @Test
    void atMostEightAndPhrasesKeepThreePlaces() {
        for (int i = 0; i < 10; i++) {
            bundledWord("go" + (char) ('a' + i), i + 1);
        }
        phrase("go away", "vi");
        phrase("go home now", "vi");
        phrase("go to bed", "vi");
        phrase("go to school", "vi");

        List<Suggestion> suggestions = service.suggest("go", null).suggestions();

        assertThat(suggestions).hasSize(8);
        assertThat(suggestions).filteredOn(s -> s.kind().equals("word")).hasSize(5);
        assertThat(suggestions).filteredOn(s -> s.kind().equals("phrase")).extracting(Suggestion::text)
                .containsExactly("go away", "go to bed", "go home now");
    }

    @Test
    void theSamePhraseInTwoLanguagesIsOneSuggestionAndLangFilters() {
        phrase("Good morning", "vi");
        phrase("Good morning", "ja");
        phrase("Good night", "ja");

        assertThat(texts("good", null)).containsExactly("Good night", "Good morning");
        assertThat(texts("good", "vi")).containsExactly("Good morning");
    }

    @Test
    void likeWildcardsInThePrefixAreJustText() {
        phrase("50% off today", "vi");
        phrase("500 people came", "vi");
        phrase("a_b test", "vi");
        phrase("abc test", "vi");

        assertThat(texts("50%", null)).containsExactly("50% off today");
        assertThat(texts("a_", null)).containsExactly("a_b test");
    }

    @Test
    void aBlankPrefixGivesAnEmptyList() {
        bundledWord("apple", 1);

        assertThat(texts("   ", null)).isEmpty();
    }

    private ResultActions suggestFrom(String q, String ip) throws Exception {
        return mockMvc.perform(get("/api/suggest").param("q", q).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }));
    }

    @Test
    void aGuestGetsTheListWithACacheHeaderAndClaudeIsNeverCalled() throws Exception {
        bundledWord("apple", 1);

        suggestFrom("ap", "203.0.113.30").andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("ap"))
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].text").value("apple"))
                .andExpect(jsonPath("$.suggestions[0].kind").value("word"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"));

        verifyNoInteractions(claude);
    }

    @Test
    void aBadLangIs400() throws Exception {
        mockMvc.perform(get("/api/suggest").param("q", "ap").param("lang", "vietnamese"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestIsRateLimitedLikeTheOtherPublicReads() throws Exception {
        for (int i = 0; i < 5; i++) {
            suggestFrom("a", "203.0.113.31").andExpect(status().isOk());
        }
        suggestFrom("a", "203.0.113.31").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }
}
