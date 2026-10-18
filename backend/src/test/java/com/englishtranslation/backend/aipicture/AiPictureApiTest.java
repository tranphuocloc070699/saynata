package com.englishtranslation.backend.aipicture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.admin.LoginRateLimiter;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.claude.ClaudeUsage;
import com.englishtranslation.backend.claude.ClaudeUsageRepository;
import com.englishtranslation.backend.claude.Feature;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.media.MediaConverter;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-91: the admin draws an AI picture for a sense. Claude, the image drawer (Codex) and ffmpeg are fakes, so no
 * test calls Claude or Codex for real. The wand's cap is 0.50 dollars and the picture cap is 2 a day in this class.
 */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "app.media.ai.enabled=true",
        "app.media.ai.pictures-per-day=2", "app.media.ai.scene-daily-usd=0.50"})
@AutoConfigureMockMvc
class AiPictureApiTest {

    static final String EXAMPLES = "[{\"level\":\"easy\",\"text\":\"I run to the park.\"}]";
    static final String SCENE_JSON = "{\"method\":\"action\",\"scene\":\"The kid runs in a park.\","
            + "\"focus\":\"the kid\",\"avoid\":\"a bike\",\"distractors\":[\"walk\",\"jump\",\"race\"]}";

    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) throws IOException {
        mediaDir = Files.createTempDirectory("saynata-ai-picture-test");
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
    JdbcTemplate jdbc;
    @Autowired
    ClaudeUsageRepository usageRows;
    @Autowired
    AiPictureJobs jobs;
    @Autowired
    AiPictureService pictures;
    @Autowired
    JsonMapper json;

    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    MediaConverter converter;
    @MockitoBean
    ImageDrawer drawer;

    Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        users.deleteAll();
        loginLimiter.reset();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
        // The fake ffmpeg copies the PNG, so different pictures give different files.
        doAnswer(call -> {
            Files.write(call.getArgument(1), (byte[]) call.getArgument(0));
            return null;
        }).when(converter).toAiPicture(any(), any());
        when(drawer.draw(anyString())).thenReturn(png(600, 400, 1));
    }

    /** Other test classes share the database and count rows, so leave nothing behind. */
    @AfterEach
    void cleanUp() throws IOException {
        clean();
    }

    private void clean() throws IOException {
        jdbc.update("delete from ai_picture_job");
        jdbc.update("delete from media_generation");
        jdbc.update("delete from word_lookup_media");
        jdbc.update("update dictionary_sense set media_id = null, media_reviewed_media_id = null");
        jdbc.update("delete from media");
        backend.deleteAll();
        bundled.deleteAll();
        sentences.deleteAll();
        usageRows.deleteAll();
        try (var files = Files.list(mediaDir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    // ---- helpers ----

    private DictionaryWord saveBundled(String lemma) {
        DictionaryWord word = new DictionaryWord(lemma, 1, "/" + lemma + "/");
        for (int i = 0; i < 2; i++) {
            DictionarySense sense = new DictionarySense(word, i);
            sense.setText("verb", "A1", "meaning " + i + " of " + lemma, "nghia", EXAMPLES, "[]");
            word.getSenses().add(sense);
        }
        DictionaryWord saved = bundled.save(word);
        jdbc.update("update dictionary_sense set image_type = 'photo', image_query = ? where word_id = ?", lemma, saved.getId());
        return saved;
    }

    private WordLookup saveBackend(String word) {
        String entry = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"B1\",\"simpleDefinition\":\"backend text\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"A " + word + ".\"}],\"usage\":[]}]}";
        WordLookup row = new WordLookup("key-" + word, word, entry, "test-model", "v7", false);
        row.setCefr("B1");
        return backend.save(row);
    }

    private static byte[] png(int width, int height, int colour) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, colour);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private ResultActions getAs(String url, Cookie cookie) throws Exception {
        return mockMvc.perform(cookie == null ? get(url) : get(url).cookie(cookie));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions draw(String wordId, int position, String prompt) throws Exception {
        return postJson("/api/admin/words/" + wordId + "/senses/" + position + "/media/ai-picture",
                json.writeValueAsString(Map.of("prompt", prompt, "source", "template")));
    }

    private JsonNode latestJob(String wordId, int position) throws Exception {
        String body = getAs("/api/admin/words/" + wordId + "/senses/" + position + "/media/ai-picture", adminCookie)
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    /** Waits until the newest job of the sense is done or failed (the worker thread runs in the background). */
    private JsonNode waitForEnd(String wordId, int position) throws Exception {
        long until = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < until) {
            JsonNode job = latestJob(wordId, position);
            if (job != null && ("done".equals(job.get("status").asString()) || "failed".equals(job.get("status").asString()))) {
                return job;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("The AI picture job did not end in 15 seconds");
    }

    private List<String> files() throws IOException {
        try (var listing = Files.list(mediaDir)) {
            return listing.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    /** A picture the sense has before the AI job: a media row, a file, and the link. */
    private void giveOldPicture(DictionaryWord word, int position, String fileName) throws IOException {
        Files.write(mediaDir.resolve(fileName), new byte[] {1, 2, 3});
        jdbc.update("insert into media (file_name, kind, bytes, source, created_at) values (?, 'photo', 3, 'pixabay', now())",
                fileName);
        jdbc.update("update dictionary_sense set media_id = (select id from media where file_name = ?),"
                + " media_reviewed_media_id = (select id from media where file_name = ?), media_reviewed_at = now()"
                + " where word_id = ? and position = ?", fileName, fileName, word.getId(), position);
    }

    private void saveSceneRow(DictionaryWord word, int position, String json, String method, String version, int minutesAgo) {
        jdbc.update("insert into media_generation (sense_id, file_name, provider, model, prompt, input_tokens, output_tokens,"
                + " cost_usd, created_at, prompt_version, method, scene_json) values ((select id from dictionary_sense"
                + " where word_id = ? and position = ?), null, 'codex', 'codex-image_gen', 'p', 0, 0, 0,"
                + " dateadd('MINUTE', ?, now()), ?, ?, ? format json)",
                word.getId(), position, -minutesAgo, version, method, json);
    }

    // ---- who may call ----

    @Test
    void withoutLoginEveryAiPictureCallIs401() throws Exception {
        DictionaryWord run = saveBundled("run");
        String base = "/api/admin/words/b-" + run.getId() + "/senses/0/media";
        getAs(base + "/ai-prompt", null).andExpect(status().isUnauthorized());
        getAs(base + "/ai-picture", null).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/ai-prompt/scene").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/ai-picture").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(drawer, never()).draw(anyString());
    }

    // ---- the prompt ----

    @Test
    void aBundledSenseWithoutASceneGetsTheTemplateWithTheMediaAiStyle() throws Exception {
        DictionaryWord run = saveBundled("run");

        getAs("/api/admin/words/b-" + run.getId() + "/senses/1/media/ai-prompt", adminCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("template"))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.startsWith(
                        "A picture that shows the meaning of the English word \"run\" (verb) for a language learner.\n"
                                + "Meaning: meaning 1 of run\n"
                                + "Example sentence (do not write it in the picture): \"I run to the park.\"\n")))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.endsWith(AiPrompts.STYLE)))
                .andExpect(jsonPath("$.usage.picturesToday").value(0))
                .andExpect(jsonPath("$.usage.picturesPerDay").value(2))
                .andExpect(jsonPath("$.usage.sceneUsdPerDay").value(0.5));
    }

    @Test
    void aBundledSenseGetsItsNewestMediaAiScene() throws Exception {
        DictionaryWord run = saveBundled("run");
        saveSceneRow(run, 0, "{\"method\":\"action\",\"scene\":\"An old scene.\",\"focus\":\"old\",\"avoid\":\"x\"}",
                "action", "ai-v1", 30);
        saveSceneRow(run, 0, SCENE_JSON, "action", "ai-v1", 10);
        // The newest row of all cannot be drawn, so it is skipped.
        saveSceneRow(run, 0, "{\"method\":\"none\",\"scene\":\"\",\"focus\":\"\",\"avoid\":\"\"}", "none", "ai-v1", 1);

        getAs("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt", adminCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("scene"))
                .andExpect(jsonPath("$.sceneVersion").value("ai-v1"))
                .andExpect(jsonPath("$.prompt").value("The kid " + AiPrompts.KID.substring(8)
                        + " runs in a park. Main focus: the kid. Must not appear: a bike.\n" + AiPrompts.STYLE));
        // The other sense has no scene: the template.
        getAs("/api/admin/words/b-" + run.getId() + "/senses/1/media/ai-prompt", adminCookie)
                .andExpect(jsonPath("$.source").value("template"));
    }

    @Test
    void aBackendWordGetsTheTemplate() throws Exception {
        WordLookup zebra = saveBackend("zebra");

        getAs("/api/admin/words/w-" + zebra.getId() + "/senses/0/media/ai-prompt", adminCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("template"))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString(
                        "the English word \"zebra\" (noun) for a language learner.\nMeaning: backend text\n"
                                + "Example sentence (do not write it in the picture): \"A zebra.\"\n")));
    }

    @Test
    void anUnknownSenseOrWordIs404() throws Exception {
        DictionaryWord run = saveBundled("run");
        getAs("/api/admin/words/b-" + run.getId() + "/senses/5/media/ai-prompt", adminCookie).andExpect(status().isNotFound());
        getAs("/api/admin/words/b-9999/senses/0/media/ai-prompt", adminCookie).andExpect(status().isNotFound());
        draw("b-" + run.getId(), 5, "x").andExpect(status().isNotFound());
    }

    // ---- the wand ----

    @Test
    void theWandAsksClaudeSonnetForANewSceneAndAnswersWithTheImagePrompt() throws Exception {
        DictionaryWord run = saveBundled("run");
        when(claude.writePictureScene(anyString(), anyString())).thenReturn("Here you go:\n" + SCENE_JSON);

        postJson("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt/scene", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("claude"))
                .andExpect(jsonPath("$.prompt").value("The kid " + AiPrompts.KID.substring(8)
                        + " runs in a park. Main focus: the kid. Must not appear: a bike.\n" + AiPrompts.STYLE));

        verify(claude).writePictureScene(AiPrompts.SCENE_SYSTEM, "Word: \"run\" (verb)\nThis sense: meaning 0 of run\n"
                + "Example: I run to the park.\nOther senses of the word (do NOT show these): "
                + "[{\"pos\": \"verb\", \"meaning\": \"meaning 1 of run\"}]");
    }

    @Test
    void aSenseClaudeCannotDrawIs422NotDrawable() throws Exception {
        DictionaryWord run = saveBundled("run");
        when(claude.writePictureScene(anyString(), anyString())).thenReturn(
                "{\"method\":\"none\",\"scene\":\"\",\"focus\":\"\",\"avoid\":\"\",\"distractors\":[]}");

        postJson("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt/scene", "{}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("not_drawable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("cannot be drawn")));
    }

    @Test
    void anAnswerThatIsNotJsonIs502WithACode() throws Exception {
        DictionaryWord run = saveBundled("run");
        when(claude.writePictureScene(anyString(), anyString())).thenReturn("Sorry, I cannot do that.");

        postJson("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt/scene", "{}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("bad_scene"));
    }

    @Test
    void theWandStopsAtItsOwnDailyCapBeforeCallingClaude() throws Exception {
        DictionaryWord run = saveBundled("run");
        // 0.60 dollars of admin spend today: over the wand's cap (0.50), under the users' budget (1.00).
        usageRows.save(new ClaudeUsage(Instant.now(), ClaudeService.SCENE_MODEL, Feature.ADMIN, 10, 10, new BigDecimal("0.60")));

        postJson("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt/scene", "{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ai_budget_reached"));
        verify(claude, never()).writePictureScene(anyString(), anyString());
        // The dialog shows the numbers.
        getAs("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt", adminCookie)
                .andExpect(jsonPath("$.usage.sceneUsdToday").value(0.6));
    }

    @Test
    void theWandAlsoStopsWhenTheUsersBudgetIsUsedUp() throws Exception {
        DictionaryWord run = saveBundled("run");
        usageRows.save(new ClaudeUsage(Instant.now(), "claude-haiku-5-5", Feature.SENTENCE, 10, 10, new BigDecimal("1.20")));

        postJson("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt/scene", "{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("daily_limit_reached"));
        verify(claude, never()).writePictureScene(anyString(), anyString());
    }

    // ---- the job ----

    @Test
    void aJobDrawsTheEditedPromptAndTheNewPictureReplacesTheOldOneForReview() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        giveOldPicture(run, 0, "run_1_oldhash1.webp");
        jdbc.update("update dictionary_sense set media_status = 'needs_ai' where word_id = ? and position = 0", run.getId());

        draw(id, 0, "  My edited prompt  ").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("queued"), org.hamcrest.Matchers.is("drawing"), org.hamcrest.Matchers.is("done"))));
        JsonNode job = waitForEnd(id, 0);

        assertThat(job.get("status").asString()).isEqualTo("done");
        assertThat(job.get("error").isNull()).isTrue();
        verify(drawer).draw("My edited prompt");
        // One new file; the old one was deleted because no sense uses it any more.
        assertThat(files()).hasSize(1).allMatch(name -> name.startsWith("run_1_ai_") && name.endsWith(".webp"));
        Map<String, Object> media = jdbc.queryForMap("select * from media where file_name = ?", files().get(0));
        assertThat(media.get("source")).isEqualTo("ai");
        assertThat(media.get("kind")).isEqualTo("photo");
        assertThat(media.get("license_note")).isEqualTo("AI image (codex)");
        assertThat(media.get("width")).isEqualTo(600);
        assertThat(media.get("height")).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from media where file_name = 'run_1_oldhash1.webp'", Integer.class))
                .isZero();
        // The sense: new picture, status ai_generated, no approval.
        Map<String, Object> sense = jdbc.queryForMap("select * from dictionary_sense where word_id = ? and position = 0", run.getId());
        assertThat(sense.get("media_id")).isEqualTo(media.get("id"));
        assertThat(sense.get("media_status")).isEqualTo("ai_generated");
        assertThat(sense.get("media_reviewed_at")).isNull();
        assertThat(sense.get("media_reviewed_media_id")).isNull();
        // The job row remembers the old and the new picture.
        Map<String, Object> row = jdbc.queryForMap("select * from ai_picture_job");
        assertThat(row.get("media_id")).isEqualTo(media.get("id"));
        assertThat(row.get("old_media_id")).isNull(); // the old media row was deleted, so SET NULL
        assertThat(row.get("prompt_source")).isEqualTo("template");
        assertThat(row.get("created_by")).isEqualTo("admin@example.com");
        // The admin word shows it, and it is in "Media to review".
        getAs("/api/admin/words/" + id, adminCookie)
                .andExpect(jsonPath("$.media[0].source").value("ai"))
                .andExpect(jsonPath("$.media[0].licenseNote").value("AI image (codex)"))
                .andExpect(jsonPath("$.mediaStatus[0]").value("ai_generated"))
                .andExpect(jsonPath("$.mediaReview[0].state").value("to_review"))
                .andExpect(jsonPath("$.mediaToReviewCount").value(1))
                .andExpect(jsonPath("$.wrongMediaCount").value(0));
    }

    @Test
    void aBackendWordGetsTheAiPictureToo() throws Exception {
        WordLookup zebra = saveBackend("zebra");
        String id = "w-" + zebra.getId();

        draw(id, 0, "A zebra on the grass.").andExpect(status().isAccepted());
        JsonNode job = waitForEnd(id, 0);

        assertThat(job.get("status").asString()).isEqualTo("done");
        assertThat(files()).hasSize(1).allMatch(name -> name.startsWith("zebra_1_ai_"));
        Map<String, Object> link = jdbc.queryForMap("select * from word_lookup_media where word_lookup_id = ? and sense_position = 0",
                zebra.getId());
        assertThat(link.get("media_status")).isEqualTo("ai_generated");
        assertThat(link.get("media_id")).isNotNull();
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.media[0].source").value("ai"));
    }

    @Test
    void anOldFileThatAnotherSenseStillUsesStays() throws Exception {
        DictionaryWord run = saveBundled("run");
        giveOldPicture(run, 0, "run_1_shared.webp");
        jdbc.update("update dictionary_sense set media_id = (select id from media where file_name = 'run_1_shared.webp')"
                + " where word_id = ? and position = 1", run.getId());

        draw("b-" + run.getId(), 0, "A prompt").andExpect(status().isAccepted());
        waitForEnd("b-" + run.getId(), 0);

        assertThat(files()).hasSize(2).contains("run_1_shared.webp");
    }

    @Test
    void ifTheAdminChangedThePictureWhileDrawingTheAiPictureStillWinsBecauseItWasAskedLast() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        giveOldPicture(run, 0, "run_1_before.webp");
        when(drawer.draw(anyString())).thenAnswer(call -> {
            // While Codex draws, the admin uploads another picture for the same sense.
            giveOldPicture(run, 0, "run_1_during.webp");
            return png(600, 400, 4);
        });

        draw(id, 0, "A prompt").andExpect(status().isAccepted());
        assertThat(waitForEnd(id, 0).get("status").asString()).isEqualTo("done");

        // The job cleans up only the picture it replaced ("during"); "before" was left by the admin's own change.
        assertThat(files()).hasSize(2).contains("run_1_before.webp")
                .anyMatch(name -> name.startsWith("run_1_ai_"));
        assertThat(jdbc.queryForObject("select m.source from dictionary_sense s join media m on m.id = s.media_id"
                + " where s.word_id = ? and s.position = 0", String.class, run.getId())).isEqualTo("ai");
    }

    @Test
    void aFailedDrawKeepsTheOldPictureAndSaysWhy() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        giveOldPicture(run, 0, "run_1_oldhash1.webp");
        when(drawer.draw(anyString())).thenThrow(new DrawException("The ChatGPT plan limit is reached. Try again later."));

        draw(id, 0, "A prompt").andExpect(status().isAccepted());
        JsonNode job = waitForEnd(id, 0);

        assertThat(job.get("status").asString()).isEqualTo("failed");
        assertThat(job.get("error").asString()).isEqualTo("The ChatGPT plan limit is reached. Try again later.");
        assertThat(files()).containsExactly("run_1_oldhash1.webp");
        Map<String, Object> sense = jdbc.queryForMap("select * from dictionary_sense where word_id = ? and position = 0", run.getId());
        assertThat(sense.get("media_id")).isNotNull();
        assertThat(sense.get("media_reviewed_at")).isNotNull(); // still approved: nothing changed
        assertThat(jdbc.queryForObject("select count(*) from media", Integer.class)).isEqualTo(1);
        // After a failure the sense can be tried again.
        org.mockito.Mockito.doReturn(png(600, 400, 2)).when(drawer).draw(anyString());
        draw(id, 0, "A prompt").andExpect(status().isAccepted());
        assertThat(waitForEnd(id, 0).get("status").asString()).isEqualTo("done");
    }

    @Test
    void aBrokenPictureFromTheDrawerEndsAsAFailedJobNotACrash() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        doAnswer(call -> {
            throw new IllegalStateException("ffmpeg is gone");
        }).when(converter).toAiPicture(any(), any());

        draw(id, 0, "A prompt").andExpect(status().isAccepted());
        JsonNode job = waitForEnd(id, 0);

        assertThat(job.get("status").asString()).isEqualTo("failed");
        assertThat(job.get("error").asString()).isEqualTo("Could not save the picture. Try again.");
        assertThat(files()).isEmpty();
    }

    @Test
    void anEmptyOrTooLongPromptIs400AndNoJobIsMade() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();

        draw(id, 0, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Write a prompt first."));
        draw(id, 0, "x".repeat(4001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4000")));
        draw(id, 0, "x".repeat(4000)).andExpect(status().isAccepted());
        waitForEnd(id, 0);

        assertThat(jdbc.queryForObject("select count(*) from ai_picture_job", Integer.class)).isEqualTo(1);
    }

    @Test
    void aSenseThatIsBeingDrawnRefusesASecondJobWith409AndShowsAiDrawing() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(drawer.draw(anyString())).thenAnswer(call -> {
            inside.countDown();
            release.await(15, TimeUnit.SECONDS);
            return png(600, 400, 3);
        });

        draw(id, 0, "First").andExpect(status().isAccepted());
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(latestJob(id, 0).get("status").asString()).isEqualTo("drawing");
        draw(id, 0, "Second").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ai_job_running"));
        // The word list shows the state, so a reloaded page keeps polling.
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.mediaStatus[0]").value("ai_drawing"));
        // Another sense of the same word is free (it waits for the one worker).
        draw(id, 1, "Other sense").andExpect(status().isAccepted());
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.mediaStatus[1]").value("ai_drawing"));

        release.countDown();
        assertThat(waitForEnd(id, 0).get("status").asString()).isEqualTo("done");
        assertThat(waitForEnd(id, 1).get("status").asString()).isEqualTo("done");
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.mediaStatus[0]").value("ai_generated"));
    }

    @Test
    void theDailyPictureCapIs429AndKeepsEverythingAsItWas() throws Exception {
        DictionaryWord run = saveBundled("run");
        WordLookup zebra = saveBackend("zebra");
        draw("b-" + run.getId(), 0, "One").andExpect(status().isAccepted());
        waitForEnd("b-" + run.getId(), 0);
        draw("b-" + run.getId(), 1, "Two").andExpect(status().isAccepted());
        waitForEnd("b-" + run.getId(), 1);

        draw("w-" + zebra.getId(), 0, "Three").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ai_picture_limit"));

        assertThat(jdbc.queryForObject("select count(*) from ai_picture_job", Integer.class)).isEqualTo(2);
        verify(drawer, org.mockito.Mockito.times(2)).draw(anyString());
        getAs("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-prompt", adminCookie)
                .andExpect(jsonPath("$.usage.picturesToday").value(2));
    }

    @Test
    void aSenseWithoutAJobHasNoLatestJob() throws Exception {
        DictionaryWord run = saveBundled("run");

        mockMvc.perform(get("/api/admin/words/b-" + run.getId() + "/senses/0/media/ai-picture").cookie(adminCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void jobsThatWereRunningWhenTheServerStoppedAreMarkedFailedAtStart() throws Exception {
        DictionaryWord run = saveBundled("run");
        String id = "b-" + run.getId();
        long queued = jobs.insert(id, 0, "p", "template", "admin");
        long drawing = jobs.insert(id, 1, "p", "template", "admin");
        jobs.markDrawing(drawing);
        long done = jobs.insert("b-999", 0, "p", "template", "admin");
        jdbc.update("update ai_picture_job set status = 'done' where id = ?", done);
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.mediaStatus[0]").value("ai_drawing"));

        pictures.failUnfinishedJobs();

        assertThat(jobs.find(queued).orElseThrow().status()).isEqualTo("failed");
        assertThat(jobs.find(queued).orElseThrow().error()).isEqualTo("The server restarted.");
        assertThat(jobs.find(drawing).orElseThrow().status()).isEqualTo("failed");
        assertThat(jobs.find(done).orElseThrow().status()).isEqualTo("done"); // finished jobs are not touched
        assertThat(latestJob(id, 1).get("error").asString()).isEqualTo("The server restarted.");
        getAs("/api/admin/words/" + id, adminCookie).andExpect(jsonPath("$.mediaStatus[0]").doesNotExist());
    }
}
