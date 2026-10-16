package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.media.MediaConverter;
import com.englishtranslation.backend.media.PixabayClient;
import com.englishtranslation.backend.media.UnreadableMediaException;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * TASK-79: the admin uploads their own picture or clip for a sense. The converter is a fake that copies its input to
 * the target, so every different upload gives a different file. The real ffmpeg is in AdminMediaUploadFfmpegTest.
 */
@SpringBootTest(properties = {"app.claude.api-key=test-key", "spring.servlet.multipart.max-file-size=3KB",
        "spring.servlet.multipart.max-request-size=4KB"})
@AutoConfigureMockMvc
class AdminMediaUploadTest {

    static final String EXAMPLES = "[{\"level\":\"easy\",\"text\":\"I run.\"}]";

    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) throws IOException {
        mediaDir = Files.createTempDirectory("saynata-admin-upload-test");
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
    AdminTokenService tokens;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ClaudeService claude;
    @MockitoBean
    PixabayClient pixabay;
    @MockitoBean
    MediaConverter converter;

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

        // The fake converter writes what it was given, so the saved file depends on the upload.
        doAnswer(call -> {
            Files.write(call.getArgument(1), (byte[]) call.getArgument(0));
            return null;
        }).when(converter).toPhoto(any(), any());
        doAnswer(call -> {
            Files.write(call.getArgument(1), (byte[]) call.getArgument(0));
            return null;
        }).when(converter).toClip(any(), any());
    }

    /** Other test classes share the database and count rows, so leave nothing behind. */
    @AfterEach
    void cleanUp() throws IOException {
        clean();
    }

    private void clean() throws IOException {
        jdbc.update("delete from word_lookup_media");
        jdbc.update("update dictionary_sense set media_id = null");
        jdbc.update("delete from media");
        backend.deleteAll();
        bundled.deleteAll();
        sentences.deleteAll();
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
        String json = "{\"senses\":[{\"pos\":\"noun\",\"cefr\":\"B1\",\"simpleDefinition\":\"backend text\","
                + "\"vietnamese\":\"x\",\"examples\":[{\"level\":\"easy\",\"text\":\"A " + word + ".\"}],\"usage\":[]}]}";
        WordLookup row = new WordLookup("key-" + word, word, json, "test-model", "v7", false);
        row.setCefr("B1");
        return backend.save(row);
    }

    /** A real tiny PNG, made with ImageIO. A different colour gives different bytes. */
    private static byte[] png(int colour) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, colour);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** The start of an MP4 file: a box of 24 bytes, "ftyp", brand "isom". Enough for the type check; the converter is a fake. */
    private static byte[] mp4(int filler) {
        byte[] bytes = new byte[40];
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        System.arraycopy(header, 0, bytes, 0, header.length);
        bytes[39] = (byte) filler;
        return bytes;
    }

    private RequestBuilder upload(String wordId, int position, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/words/" + wordId + "/senses/" + position + "/media/upload");
        return request.file(file).cookie(adminCookie).header("X-Requested-With", "XMLHttpRequest");
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private List<String> files() throws IOException {
        try (var listing = Files.list(mediaDir)) {
            return listing.map(f -> f.getFileName().toString()).toList();
        }
    }

    private Map<String, Object> mediaRow(String fileName) {
        return jdbc.queryForMap("select * from media where file_name = ?", fileName);
    }

    // ---- who may call ----

    @Test
    void withoutLoginTheUploadIs401AndNothingIsConverted() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(multipart("/api/admin/words/b-" + run.getId() + "/senses/0/media/upload")
                        .file(file("a.png", "image/png", png(1))).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        verify(converter, never()).toPhoto(any(), any());
    }

    @Test
    void aUserWithoutTheAdminRoleIsRefused() throws Exception {
        User plain = users.save(new User("plain@example.com", passwordEncoder.encode("another-long-password"), "Plain", Role.USER));
        Cookie cookie = new Cookie("saynata_admin", tokens.createToken(plain.getId()));
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(multipart("/api/admin/words/b-" + run.getId() + "/senses/0/media/upload")
                        .file(file("a.png", "image/png", png(1))).cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        verify(converter, never()).toPhoto(any(), any());
    }

    @Test
    void aFormWithoutTheCustomHeaderIsRefusedAs415() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(multipart("/api/admin/words/b-" + run.getId() + "/senses/0/media/upload")
                        .file(file("a.png", "image/png", png(1))).cookie(adminCookie))
                .andExpect(status().isUnsupportedMediaType());
        verify(converter, never()).toPhoto(any(), any());
    }

    // ---- success ----

    @Test
    void aPngBecomesThePhotoOfABundledSense() throws Exception {
        DictionaryWord run = saveBundled("run");

        mockMvc.perform(upload("b-" + run.getId(), 0, file("chatgpt.png", "image/png", png(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].kind").value("photo"))
                .andExpect(jsonPath("$.media[0].source").value("upload"))
                .andExpect(jsonPath("$.media[0].licenseNote").value("Uploaded by the admin"))
                .andExpect(jsonPath("$.media[0].photographer").doesNotExist())
                .andExpect(jsonPath("$.media[0].pageUrl").doesNotExist())
                .andExpect(jsonPath("$.media[1]").doesNotExist());

        assertThat(files()).hasSize(1).allMatch(name -> name.startsWith("run_1_") && name.endsWith(".webp"));
        Map<String, Object> row = mediaRow(files().get(0));
        assertThat(row.get("source")).isEqualTo("upload");
        assertThat(row.get("kind")).isEqualTo("photo");
        assertThat(row.get("photographer")).isNull();
        assertThat(row.get("page_url")).isNull();
        assertThat(row.get("pixabay_id")).isNull();
        assertThat(row.get("license_note")).isEqualTo("Uploaded by the admin");
        verify(converter).toPhoto(any(), any());
    }

    @Test
    void anMp4BecomesTheClipOfABackendSense() throws Exception {
        WordLookup zebra = saveBackend("zebra");

        mockMvc.perform(upload("w-" + zebra.getId(), 0, file("walk.mp4", "video/mp4", mp4(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].kind").value("clip"))
                .andExpect(jsonPath("$.media[0].source").value("upload"))
                .andExpect(jsonPath("$.media[0].url").value(org.hamcrest.Matchers.endsWith(".mp4")));

        assertThat(files()).hasSize(1).allMatch(name -> name.startsWith("zebra_1_") && name.endsWith(".mp4"));
        verify(converter).toClip(any(), any());
    }

    @Test
    void theTypeComesFromTheBytesNotFromTheNameOrTheContentType() throws Exception {
        DictionaryWord run = saveBundled("run");
        // A PNG that says it is a text file, then an MP4 that says it is a picture.
        mockMvc.perform(upload("b-" + run.getId(), 0, file("notes.txt", "text/plain", png(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].kind").value("photo"));
        mockMvc.perform(upload("b-" + run.getId(), 1, file("picture.png", "image/png", mp4(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[1].kind").value("clip"));
    }

    @Test
    void uploadingAgainReplacesTheFileAndDeletesTheOldOneWhenNothingElseUsesIt() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(upload("b-" + run.getId(), 0, file("a.png", "image/png", png(1)))).andExpect(status().isOk());
        String first = files().get(0);

        mockMvc.perform(upload("b-" + run.getId(), 0, file("b.png", "image/png", png(2)))).andExpect(status().isOk());

        assertThat(files()).hasSize(1).doesNotContain(first);
        assertThat(jdbc.queryForObject("select count(*) from media", Integer.class)).isEqualTo(1);
    }

    @Test
    void theOldFileStaysWhenAnotherSenseStillUsesIt() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(upload("b-" + run.getId(), 0, file("a.png", "image/png", png(1)))).andExpect(status().isOk());
        String shared = files().get(0);
        // Sense 2 points to the same media row.
        jdbc.update("update dictionary_sense set media_id = (select id from media where file_name = ?)"
                + " where word_id = ? and position = 1", shared, run.getId());

        mockMvc.perform(upload("b-" + run.getId(), 0, file("b.png", "image/png", png(2)))).andExpect(status().isOk());

        assertThat(files()).hasSize(2).contains(shared);
    }

    @Test
    void anUploadClearsNeedsAiAndRemovedAndGivesTheSenseAPhotoAgain() throws Exception {
        DictionaryWord run = saveBundled("run");
        WordLookup zebra = saveBackend("zebra");
        String bundledId = "b-" + run.getId();
        String backendId = "w-" + zebra.getId();

        // needs_ai on a bundled sense; "removed" on every sense of a bundled word and a backend word.
        mockMvc.perform(post("/api/admin/words/" + bundledId + "/senses/0/media/mark-for-ai").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/words/" + bundledId + "/media").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/words/" + backendId + "/media").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaStatus[0]").value("removed"));
        mockMvc.perform(post("/api/admin/words/" + bundledId + "/senses/1/media/mark-for-ai").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        mockMvc.perform(upload(bundledId, 0, file("a.png", "image/png", png(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist())
                .andExpect(jsonPath("$.mediaStatus[1]").value("needs_ai"));
        mockMvc.perform(upload(backendId, 0, file("a.png", "image/png", png(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaStatus[0]").doesNotExist());

        // The pipeline export skips a sense with image_type = none, so the upload must have switched it back.
        assertThat(jdbc.queryForObject("select image_type from dictionary_sense where word_id = ? and position = 0",
                String.class, run.getId())).isEqualTo("photo");
    }

    // ---- errors ----

    @Test
    void aFileThatIsNotAPictureOrClipIs400UnsupportedMedia() throws Exception {
        DictionaryWord run = saveBundled("run");
        byte[] text = "This is just some text, not a picture at all.".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(upload("b-" + run.getId(), 0, file("fake.png", "image/png", text)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_media"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("not a picture or a short video")));

        assertThat(files()).isEmpty();
        verify(converter, never()).toPhoto(any(), any());
        verify(converter, never()).toClip(any(), any());
    }

    @Test
    void anEmptyFileIs400UnsupportedMedia() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(upload("b-" + run.getId(), 0, file("empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unsupported_media"));
    }

    @Test
    void aBrokenFileThatLooksLikeAPictureIs400UnsupportedMediaAndKeepsTheOldMedia() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(upload("b-" + run.getId(), 0, file("a.png", "image/png", png(1)))).andExpect(status().isOk());
        String before = files().get(0);
        doThrow(new UnreadableMediaException("ffmpeg failed with exit code 1")).when(converter).toPhoto(any(), any());

        mockMvc.perform(upload("b-" + run.getId(), 0, file("broken.png", "image/png", png(9))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_media"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("could not be read")));

        assertThat(files()).containsExactly(before);
    }

    @Test
    void whenTheConverterFailsTheAnswerIs502WithTheUsualMessage() throws Exception {
        DictionaryWord run = saveBundled("run");
        doThrow(new IllegalStateException("Could not run ffmpeg")).when(converter).toPhoto(any(), any());

        mockMvc.perform(upload("b-" + run.getId(), 0, file("a.png", "image/png", png(1))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Could not download or convert that file. Try another one."));

        assertThat(files()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from media", Integer.class)).isZero();
    }

    @Test
    void aFileOverTheLimitIs413() throws Exception {
        DictionaryWord run = saveBundled("run");
        byte[] big = new byte[4 * 1024]; // the limit in this test class is 3 KB
        System.arraycopy(png(1), 0, big, 0, 8);

        mockMvc.perform(upload("b-" + run.getId(), 0, file("big.png", "image/png", big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("file_too_large"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too big")));

        verify(converter, never()).toPhoto(any(), any());
    }

    @Test
    void anUnknownWordOrSenseIs404() throws Exception {
        DictionaryWord run = saveBundled("run");
        mockMvc.perform(upload("b-999999", 0, file("a.png", "image/png", png(1)))).andExpect(status().isNotFound());
        mockMvc.perform(upload("b-" + run.getId(), 9, file("a.png", "image/png", png(1)))).andExpect(status().isNotFound());
        mockMvc.perform(upload("w-999999", 0, file("a.png", "image/png", png(1)))).andExpect(status().isNotFound());
    }
}
