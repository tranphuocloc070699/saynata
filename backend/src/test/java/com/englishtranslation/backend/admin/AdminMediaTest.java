package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.media.BackendWordMediaService;
import com.englishtranslation.backend.media.MediaConverter;
import com.englishtranslation.backend.media.PixabayBusyException;
import com.englishtranslation.backend.media.PixabayClient;
import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** TASK-52: the admin changes the picture of a sense. Pixabay and the converter are fakes. */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.media.pixabay-api-key=test-pixabay-key"})
@AutoConfigureMockMvc
class AdminMediaTest {

    static final String EXAMPLES = "[{\"level\":\"easy\",\"text\":\"I run.\"}]";

    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) throws IOException {
        mediaDir = Files.createTempDirectory("saynata-admin-media-test");
        registry.add("app.media.dir", () -> mediaDir.toString());
    }

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
    LoginRateLimiter loginLimiter;
    @Autowired
    MediaSearchRateLimiter searchLimiter;
    @Autowired
    AdminTokenService tokens;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BackendWordMediaService mediaJob;

    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    PixabayClient pixabay;
    @MockitoBean
    MediaConverter converter;

    Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("delete from word_lookup_media");
        jdbc.update("update dictionary_sense set media_id = null");
        jdbc.update("delete from media");
        backend.deleteAll();
        bundled.deleteAll();
        sentences.deleteAll();
        users.deleteAll();
        loginLimiter.reset();
        searchLimiter.reset();
        try (var files = Files.list(mediaDir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");

        // The converter writes a tiny fake file where it is told to. Pixabay "downloads" a few bytes.
        doAnswer(call -> {
            Files.write(call.getArgument(1), new byte[] {1, 2, 3, 4});
            return null;
        }).when(converter).toPhoto(any(), any());
        doAnswer(call -> {
            Files.write(call.getArgument(1), new byte[] {9, 8, 7, 6, 5});
            return null;
        }).when(converter).toClip(any(), any());
        when(pixabay.download(anyString())).thenReturn(new byte[] {42});
    }

    /** Other test classes share the database and count rows, so leave nothing behind. */
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from word_lookup_media");
        jdbc.update("update dictionary_sense set media_id = null");
        jdbc.update("delete from media");
        backend.deleteAll();
        bundled.deleteAll();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.cookie(adminCookie).contentType(MediaType.APPLICATION_JSON);
    }

    private DictionaryWord saveBundled(String lemma, String imageQuery) {
        DictionaryWord word = new DictionaryWord(lemma, 1, "/" + lemma + "/");
        for (int i = 0; i < 2; i++) {
            DictionarySense sense = new DictionarySense(word, i);
            sense.setText("verb", "A1", "meaning " + i + " of " + lemma, "nghĩa", EXAMPLES, "[]");
            word.getSenses().add(sense);
        }
        DictionaryWord saved = bundled.save(word);
        jdbc.update("update dictionary_sense set image_type = 'photo', image_query = ? where word_id = ?",
                imageQuery, saved.getId());
        return saved;
    }

    private WordLookup saveBackend(String word) {
        String json = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"B1\",\"simpleDefinition\":\"backend text\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"A " + word + ".\"}],\"usage\":[]}]}";
        WordLookup row = new WordLookup("key-" + word, word, json, "test-model", "v7", false);
        row.setCefr("B1");
        return backend.save(row);
    }

    private static PixabayHit hit(String kind, long id) {
        return new PixabayHit(kind, id, "https://cdn.pixabay.com/x/" + id, "Jane Photo",
                "https://pixabay.com/" + kind + "s/x-" + id + "/", "https://cdn.pixabay.com/preview/" + id + ".jpg");
    }

    private static String chosen(String kind, long id) {
        return "{\"kind\":\"" + kind + "\",\"pixabayId\":" + id + ",\"downloadUrl\":\"https://cdn.pixabay.com/x/" + id
                + "\",\"photographer\":\"Jane Photo\",\"pageUrl\":\"https://pixabay.com/" + kind + "s/x-" + id + "/\"}";
    }

    private List<String> files() throws IOException {
        try (var listing = Files.list(mediaDir)) {
            return listing.map(file -> file.getFileName().toString()).toList();
        }
    }

    private String fileOfSense(long wordId, int position) {
        return jdbc.queryForObject("select m.file_name from media m join dictionary_sense s on s.media_id = m.id"
                + " where s.word_id = ? and s.position = ?", String.class, wordId, position);
    }

    private int mediaRows() {
        return jdbc.queryForObject("select count(*) from media", Integer.class);
    }

    // ---- who may call ----

    @Test
    void withoutLoginEveryMediaEndpointIs401() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(get("/api/admin/media/search?sense=" + id + ":0")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/media?status=needs_ai")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/words/" + id + "/senses/0/media").contentType(MediaType.APPLICATION_JSON)
                .content(chosen("photo", 1))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/words/" + id + "/senses/0/media").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/words/" + id + "/senses/0/media/mark-for-ai").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        verify(pixabay, never()).search(anyString(), anyInt());
    }

    @Test
    void aUserWithoutTheAdminRoleIsRefused() throws Exception {
        User plain = users.save(new User("plain@example.com", passwordEncoder.encode("another-long-password"), "Plain", Role.USER));
        Cookie cookie = new Cookie("saynata_admin", tokens.createToken(plain.getId()));
        DictionaryWord run = saveBundled("run", "run picture");
        mockMvc.perform(get("/api/admin/media/search?sense=b-" + run.getId() + ":0").cookie(cookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/words/b-" + run.getId() + "/senses/0/media").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        verify(pixabay, never()).search(anyString(), anyInt());
    }

    // ---- search ----

    @Test
    void searchGivesUpToSixResultsAndUsesTheImageQueryOfTheSense() throws Exception {
        DictionaryWord run = saveBundled("run", "person running");
        when(pixabay.search("person running", 6)).thenReturn(List.of(hit("photo", 1), hit("photo", 2), hit("clip", 3)));

        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=b-" + run.getId() + ":0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("person running"))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].kind").value("photo"))
                .andExpect(jsonPath("$.results[0].previewUrl").value("https://cdn.pixabay.com/preview/1.jpg"))
                .andExpect(jsonPath("$.results[0].downloadUrl").value("https://cdn.pixabay.com/x/1"))
                .andExpect(jsonPath("$.results[0].photographer").value("Jane Photo"))
                .andExpect(jsonPath("$.results[2].kind").value("clip"));
    }

    @Test
    void searchUsesTheHeadwordForABackendWordAndTheQueryTheAdminTyped() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        when(pixabay.search("zebra", 6)).thenReturn(List.of());
        when(pixabay.search("striped horse", 6)).thenReturn(List.of(hit("photo", 9)));

        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=w-" + zebra.getId() + ":0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("zebra"))
                .andExpect(jsonPath("$.results.length()").value(0));
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=w-" + zebra.getId() + ":0&q=striped horse")))
                .andExpect(jsonPath("$.query").value("striped horse"))
                .andExpect(jsonPath("$.results.length()").value(1));
    }

    @Test
    void searchIsRateLimited() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        when(pixabay.search(anyString(), eq(6))).thenReturn(List.of());
        for (int i = 0; i < MediaSearchRateLimiter.MAX_SEARCHES; i++) {
            mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=w-" + zebra.getId() + ":0"))).andExpect(status().isOk());
        }
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=w-" + zebra.getId() + ":0")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void searchErrorsAreClear() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=b-" + run.getId() + ":5"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=b-999999:0"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=nonsense"))).andExpect(status().isBadRequest());
        when(pixabay.search(anyString(), eq(6))).thenThrow(new IllegalStateException("Pixabay search answered 500"));
        mockMvc.perform(asAdmin(get("/api/admin/media/search?sense=b-" + run.getId() + ":0")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Pixabay did not answer. Try again later."));
    }

    // ---- pick another ----

    @Test
    void pickingAnotherResultReplacesThePictureOfABundledSense() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();

        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 77)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].kind").value("photo"))
                .andExpect(jsonPath("$.media[0].photographer").value("Jane Photo"))
                .andExpect(jsonPath("$.media[0].pageUrl").value("https://pixabay.com/photos/x-77/"))
                .andExpect(jsonPath("$.media[0].url").value(org.hamcrest.Matchers.startsWith("https://api.loffy.me/media/run_1_")))
                .andExpect(jsonPath("$.media[1]").doesNotExist());

        String fileName = jdbc.queryForObject("select m.file_name from media m join dictionary_sense s on s.media_id = m.id"
                + " where s.word_id = ? and s.position = 0", String.class, run.getId());
        assertThat(mediaDir.resolve(fileName)).exists();
        assertThat(fileName).endsWith(".webp");

        // A second pick replaces the link (the new row is the one in use).
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("clip", 78)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].kind").value("clip"))
                .andExpect(jsonPath("$.media[0].url").value(org.hamcrest.Matchers.endsWith(".mp4")));
        // The first picture is not used any more, so its row and file are gone (TASK-76).
        assertThat(mediaRows()).isEqualTo(1);
        assertThat(files()).hasSize(1);
    }

    @Test
    void pickingAnotherResultWorksForABackendWordEvenWithoutAClaimRow() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        String id = "w-" + zebra.getId();
        assertThat(jdbc.queryForObject("select count(*) from word_lookup_media", Integer.class)).isZero();

        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].kind").value("photo"))
                .andExpect(jsonPath("$.media[0].url").value(org.hamcrest.Matchers.startsWith("https://api.loffy.me/media/zebra_1_")));
        assertThat(jdbc.queryForObject("select count(*) from word_lookup_media where media_id is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aChosenFileNotFromPixabayIsRefusedAndNothingIsDownloaded() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String evil = "{\"kind\":\"photo\",\"pixabayId\":1,\"downloadUrl\":\"https://evilpixabay.com/x.jpg\","
                + "\"photographer\":\"Eve\",\"pageUrl\":\"https://pixabay.com/photos/x-1/\"}";
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId() + "/senses/0/media")).content(evil))
                .andExpect(status().isBadRequest());
        String badKind = chosen("photo", 1).replace("\"photo\"", "\"movie\"");
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId() + "/senses/0/media")).content(badKind))
                .andExpect(status().isBadRequest());
        verify(pixabay, never()).download(anyString());
        assertThat(mediaRows()).isZero();
    }

    @Test
    void ifTheDownloadFailsTheOldPictureStaysAndTheAnswerIs502() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        when(pixabay.download(anyString())).thenThrow(new IllegalStateException("Pixabay download answered 500"));

        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 2)))
                .andExpect(status().isBadGateway());
        mockMvc.perform(asAdmin(get("/api/admin/words/" + id)))
                .andExpect(jsonPath("$.media[0].kind").value("photo"));
        assertThat(mediaRows()).isEqualTo(1);
    }

    @Test
    void ifPixabayIsBusyTheAdminGetsAClearMessageAndTheOldPictureStays() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        when(pixabay.download(anyString())).thenThrow(new PixabayBusyException("Pixabay answered 429"));

        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 2)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Pixabay is busy right now. Try again in a minute."));
        mockMvc.perform(asAdmin(get("/api/admin/words/" + id)))
                .andExpect(jsonPath("$.media[0].kind").value("photo"));
    }

    // ---- no image ----

    @Test
    void noImageUnlinksTheMediaOfABundledSense() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0]").doesNotExist());
        assertThat(jdbc.queryForObject("select image_type from dictionary_sense where word_id = ? and position = 0",
                String.class, run.getId())).isEqualTo("none");
        assertThat(jdbc.queryForObject("select media_id from dictionary_sense where word_id = ? and position = 0",
                Long.class, run.getId())).isNull();

        // Picking again brings the image type back.
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 3)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select image_type from dictionary_sense where word_id = ? and position = 0",
                String.class, run.getId())).isEqualTo("photo");
    }

    @Test
    void noImageForABackendWordKeepsAClaimRowSoTheJobDoesNotFetchAgain() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        String id = "w-" + zebra.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0]").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from word_lookup_media where word_lookup_id = ? and media_id is null",
                Integer.class, zebra.getId())).isEqualTo(1);
    }

    // ---- mark for AI ----

    @Test
    void markForAiIsListedByStatusForBothSourcesAndPickingClearsIt() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        WordLookup zebra = saveBackend("zebra");
        String bundledId = "b-" + run.getId();
        String backendId = "w-" + zebra.getId();

        mockMvc.perform(get("/api/admin/media?status=needs_ai").cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(asAdmin(post("/api/admin/words/" + bundledId + "/senses/1/media/mark-for-ai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist())
                .andExpect(jsonPath("$.mediaStatus[1]").value("needs_ai"));
        mockMvc.perform(asAdmin(post("/api/admin/words/" + backendId + "/senses/0/media/mark-for-ai")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaStatus[0]").value("needs_ai"));

        mockMvc.perform(get("/api/admin/media?status=needs_ai").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].wordId").value(bundledId))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].simpleDefinition").value("meaning 1 of run"))
                .andExpect(jsonPath("$[1].wordId").value(backendId))
                .andExpect(jsonPath("$[1].word").value("zebra"));

        // The word GET shows the mark too; choosing a picture or "no image" takes it away.
        mockMvc.perform(asAdmin(get("/api/admin/words/" + bundledId))).andExpect(jsonPath("$.mediaStatus[1]").value("needs_ai"));
        mockMvc.perform(asAdmin(put("/api/admin/words/" + bundledId + "/senses/1/media")).content(chosen("photo", 4)))
                .andExpect(jsonPath("$.mediaStatus[1]").doesNotExist());
        mockMvc.perform(asAdmin(delete("/api/admin/words/" + backendId + "/senses/0/media")))
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist());
        mockMvc.perform(get("/api/admin/media?status=needs_ai").cookie(adminCookie))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listWithAnotherStatusIs400() throws Exception {
        mockMvc.perform(get("/api/admin/media?status=whatever").cookie(adminCookie)).andExpect(status().isBadRequest());
    }

    @Test
    void changingTheMediaOfAMissingSenseIs404() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        mockMvc.perform(asAdmin(delete("/api/admin/words/b-" + run.getId() + "/senses/9/media"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(post("/api/admin/words/w-999999/senses/0/media/mark-for-ai"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId() + "/senses/2/media")).content(chosen("photo", 1)))
                .andExpect(status().isNotFound());
    }

    // ---- the file is really deleted (TASK-76) ----

    @Test
    void noImageDeletesTheRowAndTheFileWhenNothingElseUsesIt() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        String fileName = fileOfSense(run.getId(), 0);
        assertThat(mediaDir.resolve(fileName)).exists();

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media"))).andExpect(status().isOk());

        assertThat(mediaDir.resolve(fileName)).doesNotExist();
        assertThat(mediaRows()).isZero();
    }

    @Test
    void noImageKeepsTheFileWhileAnotherSenseStillUsesIt() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        WordLookup zebra = saveBackend("zebra");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        String fileName = fileOfSense(run.getId(), 0);
        // A backend sense and a second bundled sense use the same media row.
        jdbc.update("update dictionary_sense set media_id = (select media_id from dictionary_sense where word_id = ? and position = 0)"
                + " where word_id = ? and position = 1", run.getId(), run.getId());
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, media_id, created_at)"
                + " values (?, 0, (select media_id from dictionary_sense where word_id = ? and position = 0), now())",
                zebra.getId(), run.getId());

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media"))).andExpect(status().isOk());
        assertThat(mediaDir.resolve(fileName)).exists();
        assertThat(mediaRows()).isEqualTo(1);

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/1/media"))).andExpect(status().isOk());
        assertThat(mediaDir.resolve(fileName)).exists();

        // The last sense lets go: now the file goes.
        mockMvc.perform(asAdmin(delete("/api/admin/words/w-" + zebra.getId() + "/senses/0/media"))).andExpect(status().isOk());
        assertThat(mediaDir.resolve(fileName)).doesNotExist();
        assertThat(mediaRows()).isZero();
    }

    @Test
    void aFileThatIsAlreadyMissingIsNotAnError() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        Files.delete(mediaDir.resolve(fileOfSense(run.getId(), 0)));

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0]").doesNotExist());
        assertThat(mediaRows()).isZero();
    }

    @Test
    void pickingAnotherDeletesTheOldFileButPickingTheSameOneAgainKeepsIt() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        String first = fileOfSense(run.getId(), 0);

        // Same Pixabay result again: same content, same file name, so nothing is deleted.
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        assertThat(mediaDir.resolve(first)).exists();
        assertThat(fileOfSense(run.getId(), 0)).isEqualTo(first);

        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("clip", 2)))
                .andExpect(status().isOk());
        assertThat(mediaDir.resolve(first)).doesNotExist();
        assertThat(mediaDir.resolve(fileOfSense(run.getId(), 0))).exists();
    }

    // ---- remove all media of a word (TASK-76) ----

    @Test
    void removeAllMediaOfABundledWordClearsEverySenseAndDeletesTheFiles() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/1/media")).content(chosen("clip", 2))).andExpect(status().isOk());
        assertThat(files()).hasSize(2);
        mockMvc.perform(asAdmin(post("/api/admin/words/" + id + "/senses/1/media/mark-for-ai"))).andExpect(status().isOk());

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.media[0]").doesNotExist())
                .andExpect(jsonPath("$.media[1]").doesNotExist())
                .andExpect(jsonPath("$.mediaStatus[0]").value("removed"))
                .andExpect(jsonPath("$.mediaStatus[1]").value("removed"));

        assertThat(files()).isEmpty();
        assertThat(mediaRows()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dictionary_sense where word_id = ? and image_type = 'none'"
                + " and media_status = 'removed' and media_id is null", Integer.class, run.getId())).isEqualTo(2);
    }

    @Test
    void removeAllMediaOfABackendWordMarksEverySenseEvenWithoutAClaimRow() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        String id = "w-" + zebra.getId();
        mockMvc.perform(asAdmin(put("/api/admin/words/" + id + "/senses/0/media")).content(chosen("photo", 1))).andExpect(status().isOk());
        assertThat(files()).hasSize(1);

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/media")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0]").doesNotExist())
                .andExpect(jsonPath("$.mediaStatus[0]").value("removed"));

        assertThat(files()).isEmpty();
        assertThat(mediaRows()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from word_lookup_media where word_lookup_id = ?"
                + " and media_id is null and media_status = 'removed'", Integer.class, zebra.getId())).isEqualTo(1);
    }

    @Test
    void removeAllMediaKeepsAFileThatAnotherWordStillUses() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        DictionaryWord walk = saveBundled("walk", "walk picture");
        mockMvc.perform(asAdmin(put("/api/admin/words/b-" + run.getId() + "/senses/0/media")).content(chosen("photo", 1)))
                .andExpect(status().isOk());
        jdbc.update("update dictionary_sense set media_id = (select media_id from dictionary_sense where word_id = ? and position = 0)"
                + " where word_id = ? and position = 0", run.getId(), walk.getId());

        mockMvc.perform(asAdmin(delete("/api/admin/words/b-" + run.getId() + "/media"))).andExpect(status().isOk());

        assertThat(files()).hasSize(1);
        assertThat(fileOfSense(walk.getId(), 0)).isNotNull();
    }

    @Test
    void theBackgroundJobDoesNotFillARemovedSenseAgain() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        mockMvc.perform(asAdmin(delete("/api/admin/words/w-" + zebra.getId() + "/media"))).andExpect(status().isOk());

        WordEntry entry = new WordEntry(List.of(new WordEntry.Sense("noun", "B1", "an animal", "x",
                List.of(new WordEntry.Example("easy", "A zebra.")), List.of())));
        mediaJob.fetch(zebra.getId(), "zebra", entry);

        verify(pixabay, never()).findPhoto(anyString());
        verify(pixabay, never()).findClip(anyString());
        assertThat(files()).isEmpty();
    }

    @Test
    void removeAllMediaNeedsTheAdminRoleAndAnExistingWord() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        mockMvc.perform(delete("/api/admin/words/b-" + run.getId() + "/media").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        User plain = users.save(new User("plain@example.com", passwordEncoder.encode("another-long-password"), "Plain", Role.USER));
        Cookie cookie = new Cookie("saynata_admin", tokens.createToken(plain.getId()));
        mockMvc.perform(delete("/api/admin/words/b-" + run.getId() + "/media").cookie(cookie).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(asAdmin(delete("/api/admin/words/b-999999/media"))).andExpect(status().isNotFound());
        mockMvc.perform(asAdmin(delete("/api/admin/words/w-999999/media"))).andExpect(status().isNotFound());
    }

    // ---- picture review (TASK-80) ----

    private void addPicture(String wordId, int position, int pixabayId) throws Exception {
        mockMvc.perform(asAdmin(put("/api/admin/words/" + wordId + "/senses/" + position + "/media"))
                .content(chosen("photo", pixabayId))).andExpect(status().isOk());
    }

    /** The fake converter writes the same bytes every time, so the same media row would be reused. Make the next file different. */
    private void nextFileWillDiffer() throws Exception {
        doAnswer(call -> {
            Files.write(call.getArgument(1), new byte[] {7, 7, 7, (byte) System.nanoTime()});
            return null;
        }).when(converter).toPhoto(any(), any());
    }

    private int totalFor(String filter) throws Exception {
        String count = mockMvc.perform(get("/api/admin/words?" + filter).cookie(adminCookie))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Total-Count");
        return Integer.parseInt(count);
    }

    @Test
    void filtersAndCountsSplitWrongToReviewAndApproved() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        DictionaryWord walk = saveBundled("walk", "walk picture");
        WordLookup zebra = saveBackend("zebra");
        String runId = "b-" + run.getId();
        String walkId = "b-" + walk.getId();
        String zebraId = "w-" + zebra.getId();
        addPicture(runId, 0, 11);           // run sense 0: to review
        addPicture(runId, 1, 12);           // run sense 1: wrong
        mockMvc.perform(asAdmin(post("/api/admin/words/" + runId + "/senses/1/media/mark-for-ai"))).andExpect(status().isOk());
        addPicture(walkId, 0, 13);          // walk: approved, nothing left to review
        mockMvc.perform(asAdmin(post("/api/admin/words/" + walkId + "/senses/0/media/approve"))).andExpect(status().isOk());
        addPicture(zebraId, 0, 14);         // backend word: to review

        mockMvc.perform(asAdmin(get("/api/admin/words/" + runId)))
                .andExpect(jsonPath("$.wrongMediaCount").value(1))
                .andExpect(jsonPath("$.mediaToReviewCount").value(1))
                .andExpect(jsonPath("$.mediaReview[0].state").value("to_review"))
                .andExpect(jsonPath("$.mediaReview[1].state").value("wrong"));
        mockMvc.perform(asAdmin(get("/api/admin/words/" + walkId)))
                .andExpect(jsonPath("$.wrongMediaCount").value(0))
                .andExpect(jsonPath("$.mediaToReviewCount").value(0))
                .andExpect(jsonPath("$.mediaReview[0].state").value("approved"))
                .andExpect(jsonPath("$.mediaReview[1].state").value("none"));

        assertThat(totalFor("media=wrong")).isEqualTo(1);
        assertThat(totalFor("media=to-review")).isEqualTo(2); // run and zebra, not walk
        assertThat(totalFor("media=with")).isEqualTo(3);
        mockMvc.perform(get("/api/admin/words?media=wrong").cookie(adminCookie))
                .andExpect(jsonPath("$[0].word").value("run"))
                .andExpect(jsonPath("$[0].wrongMediaCount").value(1))
                .andExpect(jsonPath("$[0].mediaToReviewCount").value(1));
        mockMvc.perform(get("/api/admin/words?media=to-review&source=backend").cookie(adminCookie))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].word").value("zebra"));
        mockMvc.perform(get("/api/admin/words?media=nonsense").cookie(adminCookie)).andExpect(status().isBadRequest());
    }

    @Test
    void approvalBelongsToTheMediaFileSoANewPictureNeedsAReview() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        addPicture(id, 0, 21);
        mockMvc.perform(asAdmin(post("/api/admin/words/" + id + "/senses/0/media/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaReview[0].state").value("approved"));
        assertThat(totalFor("media=to-review")).isZero();

        // Pick another: a new media file, so the old approval does not count.
        nextFileWillDiffer();
        addPicture(id, 0, 22);
        mockMvc.perform(asAdmin(get("/api/admin/words/" + id)))
                .andExpect(jsonPath("$.mediaReview[0].state").value("to_review"))
                .andExpect(jsonPath("$.mediaToReviewCount").value(1));
        assertThat(totalFor("media=to-review")).isEqualTo(1);
    }

    @Test
    void approveClearsNeedsAiAndUndoMakesTheSenseToReviewAgain() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        WordLookup zebra = saveBackend("zebra");
        String id = "b-" + run.getId();
        String zebraId = "w-" + zebra.getId();
        addPicture(id, 0, 31);
        addPicture(zebraId, 0, 32);
        mockMvc.perform(asAdmin(post("/api/admin/words/" + id + "/senses/0/media/mark-for-ai"))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(post("/api/admin/words/" + zebraId + "/senses/0/media/mark-for-ai"))).andExpect(status().isOk());
        assertThat(totalFor("media=wrong")).isEqualTo(2);

        mockMvc.perform(asAdmin(post("/api/admin/words/" + id + "/senses/0/media/approve")))
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist())
                .andExpect(jsonPath("$.mediaReview[0].state").value("approved"));
        mockMvc.perform(asAdmin(post("/api/admin/words/" + zebraId + "/senses/0/media/approve")))
                .andExpect(jsonPath("$.mediaReview[0].state").value("approved"));
        assertThat(totalFor("media=wrong")).isZero();
        mockMvc.perform(get("/api/admin/media?status=needs_ai").cookie(adminCookie))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(asAdmin(delete("/api/admin/words/" + id + "/senses/0/media/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaReview[0].state").value("to_review"))
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist());
        assertThat(totalFor("media=to-review")).isEqualTo(1);
    }

    @Test
    void aSenseWithoutMediaCannotBeApproved() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        mockMvc.perform(asAdmin(post("/api/admin/words/b-" + run.getId() + "/senses/0/media/approve")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asAdmin(post("/api/admin/words/b-" + run.getId() + "/senses/9/media/approve")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theClaudeVerdictOfTheCurrentMediaIsShownReadOnly() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String id = "b-" + run.getId();
        addPicture(id, 0, 41);
        long senseId = jdbc.queryForObject("select id from dictionary_sense where word_id = ? and position = 0",
                Long.class, run.getId());
        long mediaId = jdbc.queryForObject("select media_id from dictionary_sense where id = ?", Long.class, senseId);
        jdbc.update("insert into media_check (sense_id, media_id, verdict, reason, model, checked_at, input_tokens,"
                + " output_tokens, cost_usd) values (?, ?, 'good', 'old', 'm', ?, 1, 1, 0)", senseId, mediaId,
                java.time.OffsetDateTime.now().minusDays(1));
        jdbc.update("insert into media_check (sense_id, media_id, verdict, reason, model, checked_at, input_tokens,"
                + " output_tokens, cost_usd) values (?, ?, 'wrong', 'the picture shows a toy phone', 'm', ?, 1, 1, 0)",
                senseId, mediaId, java.time.OffsetDateTime.now());

        mockMvc.perform(asAdmin(get("/api/admin/words/" + id)))
                .andExpect(jsonPath("$.mediaReview[0].verdict").value("wrong"))
                .andExpect(jsonPath("$.mediaReview[0].reason").value("the picture shows a toy phone"))
                .andExpect(jsonPath("$.mediaReview[1].verdict").doesNotExist());

        // A check of an older picture says nothing about the new one.
        nextFileWillDiffer();
        addPicture(id, 0, 42);
        mockMvc.perform(asAdmin(get("/api/admin/words/" + id)))
                .andExpect(jsonPath("$.mediaReview[0].verdict").doesNotExist());
    }

    @Test
    void approveEndpointsNeedTheAdminRole() throws Exception {
        DictionaryWord run = saveBundled("run", "run picture");
        String url = "/api/admin/words/b-" + run.getId() + "/senses/0/media/approve";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        User plain = users.save(new User("plain2@example.com", passwordEncoder.encode("another-long-password"), "Plain", Role.USER));
        Cookie cookie = new Cookie("saynata_admin", tokens.createToken(plain.getId()));
        mockMvc.perform(post(url).cookie(cookie).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
