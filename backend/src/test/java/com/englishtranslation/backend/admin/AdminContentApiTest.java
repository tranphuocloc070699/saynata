package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.translation.SentenceTranslation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
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

/** The words and sentences admin endpoints, called as a logged-in admin. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminContentApiTest {

    static final String GOOD_ENTRY = """
            {"entry": {"senses": [{
              "pos": "verb", "cefr": "A1", "simpleDefinition": "to move fast on your feet", "vietnamese": "chạy",
              "examples": [
                {"level": "easy", "text": "I run."},
                {"level": "medium", "text": "She runs every morning."},
                {"level": "harder", "text": "He ran to catch the last bus."}],
              "usage": [{"kind": "pattern", "pattern": "run + to + place", "example": "I run to the park."}]
            }]}}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    WordLookupRepository words;

    @Autowired
    SentenceTranslationRepository sentences;

    @Autowired
    LoginRateLimiter rateLimiter;

    @MockitoBean
    ClaudeService claude;

    Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        words.deleteAll();
        sentences.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(adminCookie).contentType(MediaType.APPLICATION_JSON);
    }

    private WordLookup saveWord(String word, boolean needsReview) {
        String json = "{\"senses\":[{\"pos\":\"verb\",\"cefr\":\"A1\",\"simpleDefinition\":\"old text\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"I " + word + ".\"}],\"usage\":[]}]}";
        return words.save(new WordLookup("key-" + word, word, json, "test-model", "v7", needsReview));
    }

    private SentenceTranslation saveSentence(String text, boolean needsReview) {
        SentenceTranslation row = new SentenceTranslation("key-" + text, text, "vi", "A greeting.",
                "[{\"word\":\"hello\",\"partOfSpeech\":\"interjection\",\"meaning\":\"a greeting\"}]",
                "Xin chào", "test-model", "v6");
        row.setNeedsReview(needsReview);
        return sentences.save(row);
    }

    // ---- words ----

    @Test
    void wordListHasPagingSearchAndTotalCount() throws Exception {
        for (String word : new String[] { "run", "runner", "walk", "jump", "swim" }) {
            saveWord(word, false);
        }

        mockMvc.perform(asAdmin(get("/api/admin/words?_start=0&_end=2&_sort=word&_order=ASC")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].word").value("jump"))
                .andExpect(jsonPath("$[1].word").value("run"))
                .andExpect(jsonPath("$[0].entry.senses[0].simpleDefinition").value("old text"));

        mockMvc.perform(asAdmin(get("/api/admin/words?_start=2&_end=4&_sort=word&_order=ASC")))
                .andExpect(jsonPath("$[0].word").value("runner"))
                .andExpect(jsonPath("$[1].word").value("swim"));

        mockMvc.perform(asAdmin(get("/api/admin/words?q=RUN&_start=0&_end=10&_sort=word&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[1].word").value("runner"));
    }

    @Test
    void searchTreatsPercentAsPlainText() throws Exception {
        saveWord("run", false);

        mockMvc.perform(asAdmin(get("/api/admin/words?q=%25")))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void wordListCanFilterByNeedsReview() throws Exception {
        saveWord("run", true);
        saveWord("walk", false);

        mockMvc.perform(asAdmin(get("/api/admin/words?needs_review=true")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].word").value("run"));
        mockMvc.perform(asAdmin(get("/api/admin/words?needs_review=false")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].word").value("walk"));
        mockMvc.perform(asAdmin(get("/api/admin/words")))
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void badListParametersGive400() throws Exception {
        mockMvc.perform(asAdmin(get("/api/admin/words?_sort=password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("_sort must be one of")));
        mockMvc.perform(asAdmin(get("/api/admin/words?_start=3&_end=10")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOneWordAndUnknownIdGives404() throws Exception {
        WordLookup run = saveWord("run", true);

        mockMvc.perform(asAdmin(get("/api/admin/words/w-" + run.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("w-" + run.getId()))
                .andExpect(jsonPath("$.word").value("run"))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.promptVersion").value("v7"));
        mockMvc.perform(asAdmin(get("/api/admin/words/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void updateWordSavesTheEntryAndThePublicApiShowsIt() throws Exception {
        WordLookup run = saveWord("run", false);

        mockMvc.perform(asAdmin(put("/api/admin/words/w-" + run.getId())).content(GOOD_ENTRY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses[0].simpleDefinition").value("to move fast on your feet"));

        assertThat(words.findById(run.getId()).orElseThrow().getEntryJson()).contains("to move fast on your feet");
    }

    @Test
    void updateWordIgnoresTheOtherFieldsRefineSendsBack() throws Exception {
        WordLookup run = saveWord("run", false);
        String wholeRecord = GOOD_ENTRY.replace("{\"entry\"", "{\"id\": 5, \"word\": \"hacked\", \"needsReview\": true, \"entry\"");

        mockMvc.perform(asAdmin(put("/api/admin/words/w-" + run.getId())).content(wholeRecord))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("run"));
    }

    @Test
    void updateWordWithAnInvalidEntryGives400AndSavesNothing() throws Exception {
        WordLookup run = saveWord("run", false);
        String before = words.findById(run.getId()).orElseThrow().getEntryJson();
        String[] badBodies = {
                "{}", // no entry
                "{\"entry\": {\"senses\": []}}", // no senses
                "{\"entry\": {\"senses\": \"text\"}}", // wrong type
                "{\"entry\": {\"sensess\": []}}", // typo in a field name
                GOOD_ENTRY.replace("\"easy\"", "\"simple\""), // bad example level
                GOOD_ENTRY.replace("\"pattern\"", "\"rule\""), // bad usage kind
                GOOD_ENTRY.replace("to move fast on your feet", " "), // empty definition
        };

        for (String body : badBodies) {
            mockMvc.perform(asAdmin(put("/api/admin/words/w-" + run.getId())).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        assertThat(words.findById(run.getId()).orElseThrow().getEntryJson()).isEqualTo(before);
    }

    @Test
    void invalidEntryMessageNamesTheProblem() throws Exception {
        WordLookup run = saveWord("run", false);

        mockMvc.perform(asAdmin(put("/api/admin/words/w-" + run.getId())).content(GOOD_ENTRY.replace("\"easy\"", "\"simple\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("entry.senses[0].examples[0].level must be one of: easy, harder, medium."));
    }

    @Test
    void markWordReviewed() throws Exception {
        WordLookup run = saveWord("run", true);

        mockMvc.perform(asAdmin(post("/api/admin/words/w-" + run.getId() + "/reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false));
        assertThat(words.findById(run.getId()).orElseThrow().isNeedsReview()).isFalse();
    }

    @Test
    void deleteWordRemovesTheRowSoTheNextLookupRegenerates() throws Exception {
        WordLookup run = saveWord("run", false);

        mockMvc.perform(asAdmin(delete("/api/admin/words/w-" + run.getId()))).andExpect(status().isOk());

        assertThat(words.findById(run.getId())).isEmpty();
        mockMvc.perform(asAdmin(delete("/api/admin/words/w-" + run.getId()))).andExpect(status().isNotFound());
    }

    // ---- sentences ----

    @Test
    void sentenceListHasSearchPagingAndNeedsReviewFilter() throws Exception {
        saveSentence("hello there", true);
        saveSentence("hello again", false);
        saveSentence("good night", false);

        mockMvc.perform(asAdmin(get("/api/admin/sentences?q=hello&_start=0&_end=10&_sort=sourceText&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].sourceText").value("hello again"))
                .andExpect(jsonPath("$[0].explanation.meaning").value("A greeting."))
                .andExpect(jsonPath("$[0].explanation.words[0].word").value("hello"))
                .andExpect(jsonPath("$[0].explanation.translation").value("Xin chào"))
                .andExpect(jsonPath("$[0].targetLang").value("vi"));
        mockMvc.perform(asAdmin(get("/api/admin/sentences?needs_review=true")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].sourceText").value("hello there"));
        mockMvc.perform(asAdmin(get("/api/admin/sentences?_start=0&_end=2")))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getOneSentence() throws Exception {
        SentenceTranslation row = saveSentence("hello there", false);

        mockMvc.perform(asAdmin(get("/api/admin/sentences/" + row.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceText").value("hello there"));
        mockMvc.perform(asAdmin(get("/api/admin/sentences/999999"))).andExpect(status().isNotFound());
    }

    @Test
    void updateSentenceSavesTheExplanation() throws Exception {
        SentenceTranslation row = saveSentence("hello there", false);
        String body = """
                {"explanation": {"meaning": "A friendly way to say hi.",
                  "words": [{"word": "there", "partOfSpeech": "adverb", "meaning": "in that place"}],
                  "translation": "Chào bạn"}}
                """;

        mockMvc.perform(asAdmin(put("/api/admin/sentences/" + row.getId())).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation.meaning").value("A friendly way to say hi."))
                .andExpect(jsonPath("$.explanation.words[0].word").value("there"))
                .andExpect(jsonPath("$.explanation.translation").value("Chào bạn"));

        SentenceTranslation saved = sentences.findById(row.getId()).orElseThrow();
        assertThat(saved.getMeaning()).isEqualTo("A friendly way to say hi.");
        assertThat(saved.getWordsJson()).contains("in that place");
    }

    @Test
    void updateSentenceWithAnInvalidExplanationGives400() throws Exception {
        SentenceTranslation row = saveSentence("hello there", false);
        String[] badBodies = {
                "{}",
                "{\"explanation\": {\"meaning\": \"\", \"words\": [], \"translation\": \"x\"}}",
                "{\"explanation\": {\"meaning\": \"ok\", \"translation\": \"x\"}}", // words missing
                "{\"explanation\": {\"meaning\": \"ok\", \"words\": [{\"word\": \"a\"}], \"translation\": \"x\"}}",
                "{\"explanation\": {\"meaning\": \"ok\", \"words\": \"no\", \"translation\": \"x\"}}",
        };

        for (String body : badBodies) {
            mockMvc.perform(asAdmin(put("/api/admin/sentences/" + row.getId())).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
        assertThat(sentences.findById(row.getId()).orElseThrow().getMeaning()).isEqualTo("A greeting.");
    }

    @Test
    void markSentenceReviewedAndDeleteIt() throws Exception {
        SentenceTranslation row = saveSentence("hello there", true);

        mockMvc.perform(asAdmin(post("/api/admin/sentences/" + row.getId() + "/reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false));
        mockMvc.perform(asAdmin(delete("/api/admin/sentences/" + row.getId()))).andExpect(status().isOk());

        assertThat(sentences.findById(row.getId())).isEmpty();
    }

    @Test
    void writeEndpointsNeedLoginToo() throws Exception {
        WordLookup run = saveWord("run", false);

        mockMvc.perform(put("/api/admin/words/w-" + run.getId()).contentType(MediaType.APPLICATION_JSON).content(GOOD_ENTRY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/words/w-" + run.getId()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        assertThat(words.findById(run.getId())).isPresent();
    }
}
