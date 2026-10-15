package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The bundled words (table dictionary_word) in the admin words API, next to the backend words. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminBundledWordsTest {

    static final String EXAMPLES = "[{\"level\":\"easy\",\"text\":\"I run.\"},{\"level\":\"medium\",\"text\":\"She runs.\"},"
            + "{\"level\":\"harder\",\"text\":\"He ran to the bus.\"}]";
    static final String USAGE = "[{\"kind\":\"phrase\",\"pattern\":\"run away\",\"example\":\"The dog ran away.\"}]";

    static final String NEW_ENTRY = """
            {"entry": {"senses": [
              {"pos": "verb", "cefr": "A1", "simpleDefinition": "to move fast on your feet", "vietnamese": "chạy",
               "examples": [{"level": "easy", "text": "I run every day."}], "usage": []},
              {"pos": "noun", "cefr": "B1", "simpleDefinition": "a fast walk for exercise", "vietnamese": "cuộc chạy",
               "examples": [{"level": "easy", "text": "I went for a run."}], "usage": []}]}}
            """;

    static final String ONE_SENSE_ENTRY = """
            {"entry": {"senses": [
              {"pos": "verb", "cefr": "A1", "simpleDefinition": "to go fast", "vietnamese": "chạy",
               "examples": [{"level": "easy", "text": "I run."}], "usage": []}]}}
            """;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    WordLookupRepository backend;
    @Autowired
    SentenceTranslationRepository sentences;
    @Autowired
    LoginRateLimiter rateLimiter;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ClaudeService claude;

    Cookie adminCookie;

    /** The database is shared with other test classes (AdminContentApiTest counts words), so leave no words behind. */
    @AfterEach
    void cleanUp() {
        backend.deleteAll();
        bundled.deleteAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        backend.deleteAll();
        bundled.deleteAll();
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

    /** A bundled word with one sense per level given; every sense has a picture ("photo", query "<lemma> picture"). */
    private DictionaryWord saveBundled(String lemma, int rank, boolean needsReview, String... levels) {
        DictionaryWord word = new DictionaryWord(lemma, rank, "/" + lemma + "/");
        word.setNeedsReview(needsReview);
        for (int i = 0; i < levels.length; i++) {
            DictionarySense sense = new DictionarySense(word, i);
            sense.setText("verb", levels[i], "meaning " + i + " of " + lemma, "nghĩa", EXAMPLES, USAGE);
            word.getSenses().add(sense);
        }
        DictionaryWord saved = bundled.save(word);
        jdbc.update("update dictionary_sense set image_type = 'photo', image_query = ? where word_id = ?",
                lemma + " picture", saved.getId());
        return saved;
    }

    private WordLookup saveBackend(String word, String cefr, boolean needsReview) {
        String json = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"" + cefr + "\",\"simpleDefinition\":\"backend text\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"A " + word + ".\"}],\"usage\":[]}]}";
        WordLookup row = new WordLookup("key-" + word, word, json, "test-model", "v7", needsReview);
        row.setCefr(cefr);
        return backend.save(row);
    }

    @Test
    void listShowsBundledAndBackendWordsWithTheirSourceAndPrefixedIds() throws Exception {
        DictionaryWord run = saveBundled("run", 5, false, "A1");
        WordLookup zebra = saveBackend("zebra", "B1", false);
        // The two tables count ids separately, so the same number can exist in both. The letter keeps them apart.
        mockMvc.perform(asAdmin(get("/api/admin/words?_start=0&_end=10&_sort=word&_order=ASC")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].id").value("b-" + run.getId()))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[0].source").value("bundled"))
                .andExpect(jsonPath("$[0].ipa").value("/run/"))
                .andExpect(jsonPath("$[0].entry.senses[0].simpleDefinition").value("meaning 0 of run"))
                .andExpect(jsonPath("$[0].entry.senses[0].examples.length()").value(3))
                .andExpect(jsonPath("$[0].entry.senses[0].usage[0].kind").value("phrase"))
                .andExpect(jsonPath("$[1].id").value("w-" + zebra.getId()))
                .andExpect(jsonPath("$[1].source").value("backend"))
                .andExpect(jsonPath("$[1].ipa").doesNotExist())
                .andExpect(jsonPath("$[1].entry.senses[0].simpleDefinition").value("backend text"));
    }

    @Test
    void listCanFilterBySource() throws Exception {
        saveBundled("run", 5, false, "A1");
        saveBundled("walk", 6, false, "A1");
        saveBackend("zebra", "B1", false);

        mockMvc.perform(asAdmin(get("/api/admin/words?source=bundled&_sort=word&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[1].word").value("walk"));
        mockMvc.perform(asAdmin(get("/api/admin/words?source=backend")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].word").value("zebra"));
        mockMvc.perform(asAdmin(get("/api/admin/words?source=other")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("source must be one of")));
    }

    @Test
    void listCanFilterByCefrOfTheFirstSense() throws Exception {
        saveBundled("run", 1, false, "A1", "C1"); // first sense A1; the second sense does not count
        saveBundled("walk", 2, false, "A2");
        saveBundled("analyse", 3, false, "B2");
        saveBackend("zebra", "B1", false);

        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=A1,A2&_sort=word&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[1].word").value("walk"));
        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=B1,B2&_sort=word&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].word").value("analyse"))
                .andExpect(jsonPath("$[1].word").value("zebra"));
        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=b2&source=bundled")))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=C1")))
                .andExpect(header().string("X-Total-Count", "0"));
        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=Z9")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cefr must be a list of")));
    }

    @Test
    void filtersWorkTogetherWithSearchAndNeedsReview() throws Exception {
        saveBundled("run", 1, true, "A1");
        saveBundled("runner", 2, false, "A1");
        saveBackend("running", "A1", true);

        mockMvc.perform(asAdmin(get("/api/admin/words?q=RUN&needs_review=true&_sort=word&_order=ASC")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[1].word").value("running"));
        mockMvc.perform(asAdmin(get("/api/admin/words?q=run&needs_review=true&source=bundled&cefr=A1")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].word").value("run"));
    }

    @Test
    void listPagesAcrossBothSourcesAndSortsById() throws Exception {
        saveBundled("a", 1, false, "A1");
        saveBundled("b", 2, false, "A1");
        saveBackend("c", "A1", false);

        // Sorting by id means: by source ("bundled" before "backend" when descending), then by the number.
        mockMvc.perform(asAdmin(get("/api/admin/words?_start=0&_end=2")))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].word").value("b"))
                .andExpect(jsonPath("$[1].word").value("a"));
        mockMvc.perform(asAdmin(get("/api/admin/words?_start=2&_end=4")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].word").value("c"));
        mockMvc.perform(asAdmin(get("/api/admin/words?_sort=id&_order=ASC")))
                .andExpect(jsonPath("$[0].word").value("c"))
                .andExpect(jsonPath("$[1].word").value("a"));
    }

    @Test
    void getOneBundledWordAndNumbersWithoutALetterAre404() throws Exception {
        DictionaryWord run = saveBundled("run", 1, true, "A1", "B1");

        mockMvc.perform(asAdmin(get("/api/admin/words/b-" + run.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("b-" + run.getId()))
                .andExpect(jsonPath("$.source").value("bundled"))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.entry.senses.length()").value(2))
                .andExpect(jsonPath("$.entry.senses[1].cefr").value("B1"));
        mockMvc.perform(asAdmin(get("/api/admin/words/b-999999"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/admin/words/" + run.getId()))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/admin/words/x-1"))).andExpect(status().isNotFound());
    }

    @Test
    void updateBundledWordChangesTheDictionaryTablesAndKeepsThePictureFields() throws Exception {
        DictionaryWord run = saveBundled("run", 1, false, "A1");

        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId())).content(NEW_ENTRY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("b-" + run.getId()))
                .andExpect(jsonPath("$.entry.senses.length()").value(2))
                .andExpect(jsonPath("$.entry.senses[0].examples.length()").value(1));

        assertThat(jdbc.queryForList(
                "select position, cefr, simple_definition, image_type, image_query, examples_json, usage_json"
                        + " from dictionary_sense where word_id = ? order by position", run.getId()))
                .satisfiesExactly(
                        first -> {
                            assertThat(first.get("simple_definition")).isEqualTo("to move fast on your feet");
                            assertThat(first.get("image_type")).isEqualTo("photo"); // kept
                            assertThat(first.get("image_query")).isEqualTo("run picture"); // kept
                            assertThat((String) first.get("examples_json")).contains("I run every day.");
                            assertThat(first.get("usage_json")).isEqualTo("[]");
                        },
                        second -> {
                            assertThat(second.get("cefr")).isEqualTo("B1");
                            assertThat(second.get("image_type")).isEqualTo("none"); // a new sense has no picture
                        });

        // A shorter entry removes the senses at the end.
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId())).content(ONE_SENSE_ENTRY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from dictionary_sense where word_id = ?", Integer.class,
                run.getId())).isEqualTo(1);
    }

    @Test
    void updateBundledWordIsValidatedLikeABackendWordAndSavesNothingOnError() throws Exception {
        DictionaryWord run = saveBundled("run", 1, false, "A1");
        String bad = NEW_ENTRY.replace("\"easy\"", "\"simple\"");

        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId())).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("entry.senses[0].examples[0].level must be one of")));
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId())).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select simple_definition from dictionary_sense where word_id = ?",
                String.class, run.getId())).isEqualTo("meaning 0 of run");
    }

    @Test
    void updateBackendWordKeepsItsLevelForTheCefrFilter() throws Exception {
        WordLookup zebra = saveBackend("zebra", "B1", false);

        mockMvc.perform(asAdmin(put("/api/admin/words/w-" + zebra.getId())).content(NEW_ENTRY))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=A1")))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(asAdmin(get("/api/admin/words?cefr=B1")))
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void markBundledWordReviewed() throws Exception {
        DictionaryWord run = saveBundled("run", 1, true, "A1");

        mockMvc.perform(asAdmin(post("/api/admin/words/b-" + run.getId() + "/reviewed")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false));
        assertThat(bundled.findById(run.getId()).orElseThrow().isNeedsReview()).isFalse();
    }

    @Test
    void markBackendWordReviewedClearsTheFlagAndItStaysClearedWhenReadBack() throws Exception {
        // "working" and "apple" are backend words that are flagged in production (TASK-57).
        WordLookup hard = saveHardBackend("working", true);
        WordLookup other = saveHardBackend("apple", true);

        // The admin app sends POST /reviewed with an empty JSON object as the body.
        mockMvc.perform(asAdmin(post("/api/admin/words/w-" + hard.getId() + "/reviewed")).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.reviewReasons.length()").value(0));

        // Read back: the repository, the detail, and the "Needs review" list (only the other word is left).
        assertThat(backend.findById(hard.getId()).orElseThrow().isNeedsReview()).isFalse();
        assertThat(jdbc.queryForObject("select needs_review from word_lookup where id = ?", Boolean.class, hard.getId()))
                .isFalse();
        mockMvc.perform(asAdmin(get("/api/admin/words/w-" + hard.getId())))
                .andExpect(jsonPath("$.needsReview").value(false));
        mockMvc.perform(asAdmin(get("/api/admin/words?needs_review=true")))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].word").value("apple"));
        assertThat(backend.findById(other.getId()).orElseThrow().isNeedsReview()).isTrue();
    }

    @Test
    void aBundledWordCannotBeDeleted() throws Exception {
        DictionaryWord run = saveBundled("run", 1, false, "A1");

        mockMvc.perform(asAdmin(delete("/api/admin/words/b-" + run.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be deleted")));
        assertThat(bundled.findById(run.getId())).isPresent();
        mockMvc.perform(asAdmin(delete("/api/admin/words/b-999999"))).andExpect(status().isNotFound());
    }

    /** Makes a media row (a Pixabay photo or clip) and returns its id. */
    private long saveMedia(String fileName, String kind, String photographer) {
        jdbc.update("insert into media (file_name, kind, photographer, page_url, license_note, created_at)"
                + " values (?, ?, ?, ?, 'Pixabay Content License', current_timestamp)",
                fileName, kind, photographer, "https://pixabay.com/x/" + fileName);
        return jdbc.queryForObject("select id from media where file_name = ?", Long.class, fileName);
    }

    @Test
    void wordsCarryTheMediaOfEachSenseWithTheCredit() throws Exception {
        DictionaryWord run = saveBundled("run", 1, false, "A1", "B1");
        long clipId = saveMedia("admin_test_run_1.mp4", "clip", "Pat Maker");
        jdbc.update("update dictionary_sense set media_id = ? where word_id = ? and position = 0", clipId, run.getId());
        WordLookup zebra = saveBackend("zebra", "B1", false);
        long photoId = saveMedia("admin_test_zebra_1.webp", "photo", "Zoe Camera");
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, media_id, created_at)"
                + " values (?, 0, ?, current_timestamp)", zebra.getId(), photoId);
        try {
            // one bundled word: sense 1 has a clip, sense 2 has none (null keeps the order of the senses)
            mockMvc.perform(asAdmin(get("/api/admin/words/b-" + run.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.media.length()").value(2))
                    .andExpect(jsonPath("$.media[0].kind").value("clip"))
                    .andExpect(jsonPath("$.media[0].url").value("https://api.loffy.me/media/admin_test_run_1.mp4"))
                    .andExpect(jsonPath("$.media[0].photographer").value("Pat Maker"))
                    .andExpect(jsonPath("$.media[0].pageUrl").value("https://pixabay.com/x/admin_test_run_1.mp4"))
                    .andExpect(jsonPath("$.media[1]").doesNotExist());
            // the list has them too
            mockMvc.perform(asAdmin(get("/api/admin/words?_start=0&_end=10&_sort=word&_order=ASC")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].media[0].photographer").value("Pat Maker"))
                    .andExpect(jsonPath("$[1].media[0].kind").value("photo"))
                    .andExpect(jsonPath("$[1].media[0].photographer").value("Zoe Camera"));
        } finally {
            backend.deleteAll();
            bundled.deleteAll();
            jdbc.update("delete from media where file_name like 'admin_test_%'");
        }
    }

    @Test
    void listCanFilterByMedia() throws Exception {
        // Any sense with media counts: here only the second sense of "run" has a clip.
        DictionaryWord run = saveBundled("run", 1, false, "A1", "B1");
        saveBundled("walk", 2, false, "A1");
        WordLookup zebra = saveBackend("zebra", "B1", false);
        WordLookup apple = saveBackend("apple", "A1", false);
        saveBackend("pear", "A1", false);
        long clipId = saveMedia("admin_test_media_filter_1.mp4", "clip", "Pat Maker");
        long photoId = saveMedia("admin_test_media_filter_2.webp", "photo", "Zoe Camera");
        jdbc.update("update dictionary_sense set media_id = ? where word_id = ? and position = 1", clipId, run.getId());
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, media_id, created_at)"
                + " values (?, 0, ?, current_timestamp)", zebra.getId(), photoId);
        // A claim row with no file (Pixabay found nothing) is not media.
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, created_at)"
                + " values (?, 0, current_timestamp)", apple.getId());
        try {
            mockMvc.perform(asAdmin(get("/api/admin/words?media=with&_sort=word&_order=ASC")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "2"))
                    .andExpect(jsonPath("$[0].word").value("run"))
                    .andExpect(jsonPath("$[1].word").value("zebra"));
            mockMvc.perform(asAdmin(get("/api/admin/words?media=without&_sort=word&_order=ASC")))
                    .andExpect(header().string("X-Total-Count", "3"))
                    .andExpect(jsonPath("$[0].word").value("apple"))
                    .andExpect(jsonPath("$[1].word").value("pear"))
                    .andExpect(jsonPath("$[2].word").value("walk"));
            // It works together with the other filters and the total follows the filter.
            mockMvc.perform(asAdmin(get("/api/admin/words?media=with&source=backend")))
                    .andExpect(header().string("X-Total-Count", "1"))
                    .andExpect(jsonPath("$[0].word").value("zebra"));
            mockMvc.perform(asAdmin(get("/api/admin/words?media=without&source=bundled&q=wal")))
                    .andExpect(header().string("X-Total-Count", "1"))
                    .andExpect(jsonPath("$[0].word").value("walk"));
            mockMvc.perform(asAdmin(get("/api/admin/words?media=with&cefr=A1")))
                    .andExpect(header().string("X-Total-Count", "1"))
                    .andExpect(jsonPath("$[0].word").value("run"));
            mockMvc.perform(asAdmin(get("/api/admin/words?media=some")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("media must be one of")));
        } finally {
            backend.deleteAll();
            bundled.deleteAll();
            jdbc.update("delete from media where file_name like 'admin_test_%'");
        }
    }

    /** A backend word whose definition uses the hard word "abstain". */
    private WordLookup saveHardBackend(String word, boolean needsReview) {
        String json = "{\"senses\":[{\"pos\":\"verb\",\"cefr\":\"B1\",\"simpleDefinition\":\"to abstain from food\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"I " + word + ".\"}],\"usage\":[]}]}";
        WordLookup row = new WordLookup("key-" + word, word, json, "test-model", "v7", needsReview);
        row.setCefr("B1");
        return backend.save(row);
    }

    @Test
    void aFlaggedWordCarriesItsReviewReasonsAndACleanWordHasNone() throws Exception {
        WordLookup hard = saveHardBackend("working", true);
        WordLookup clean = saveBackend("zebra", "B1", false);

        mockMvc.perform(asAdmin(get("/api/admin/words/w-" + hard.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewReasons.length()").value(1))
                .andExpect(jsonPath("$.reviewReasons[0]").value(containsString("sense 1: the definition uses hard words")))
                .andExpect(jsonPath("$.reviewReasons[0]").value(containsString("abstain")));
        mockMvc.perform(asAdmin(get("/api/admin/words/w-" + clean.getId())))
                .andExpect(jsonPath("$.reviewReasons.length()").value(0));
        // The list items carry them too.
        mockMvc.perform(asAdmin(get("/api/admin/words?_start=0&_end=10&_sort=word&_order=ASC&needs_review=true")))
                .andExpect(jsonPath("$[0].word").value("working"))
                .andExpect(jsonPath("$[0].reviewReasons[0]").value(containsString("abstain")));
    }

    @Test
    void reasonsAreRecomputedAfterAnEdit() throws Exception {
        WordLookup hard = saveHardBackend("working", true);
        mockMvc.perform(asAdmin(put("/api/admin/words/w-" + hard.getId())).content(ONE_SENSE_ENTRY))
                .andExpect(status().isOk())
                // Fixed, but the flag stays until the developer marks it reviewed.
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.reviewReasons.length()").value(0));
    }
}
