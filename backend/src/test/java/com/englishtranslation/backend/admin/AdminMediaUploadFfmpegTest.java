package com.englishtranslation.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.englishtranslation.backend.word.WordLookupRepository;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** TASK-79: an upload through the REAL ffmpeg converter. Skipped when ffmpeg is not installed. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminMediaUploadFfmpegTest {

    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) throws IOException {
        mediaDir = Files.createTempDirectory("saynata-admin-upload-ffmpeg-test");
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

    @MockitoBean
    ClaudeService claude;

    Cookie adminCookie;
    DictionaryWord run;

    private static boolean ffmpegInstalled() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeThat(ffmpegInstalled()).isTrue();
        clean();
        users.deleteAll();
        loginLimiter.reset();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        adminCookie = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
        DictionaryWord word = new DictionaryWord("run", 1, "/run/");
        DictionarySense sense = new DictionarySense(word, 0);
        sense.setText("verb", "A1", "to move fast", "chay", "[{\"level\":\"easy\",\"text\":\"I run.\"}]", "[]");
        word.getSenses().add(sense);
        run = bundled.save(word);
    }

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

    private org.springframework.test.web.servlet.RequestBuilder upload(MockMultipartFile file) {
        return multipart("/api/admin/words/b-" + run.getId() + "/senses/0/media/upload").file(file).cookie(adminCookie)
                .header("X-Requested-With", "XMLHttpRequest");
    }

    @Test
    void aBigPngBecomesA320PxWebp() throws Exception {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().fillRect(0, 0, 500, 500);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        mockMvc.perform(upload(new MockMultipartFile("file", "big.png", "image/png", png.toByteArray())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].kind").value("photo"));

        Path saved;
        try (var listing = Files.list(mediaDir)) {
            saved = listing.findFirst().orElseThrow();
        }
        byte[] webp = Files.readAllBytes(saved);
        assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        // In a lossy WebP ("VP8 " chunk) the width is a 14-bit number at byte 26.
        int width = ((webp[27] & 0x3F) << 8) | (webp[26] & 0xFF);
        assertThat(width).isEqualTo(320);
    }

    @Test
    void aFiveSecondMp4BecomesAShortMp4() throws Exception {
        Path source = Files.createTempFile("upload-source", ".mp4");
        Process make = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "testsrc=duration=5:size=640x360:rate=10", "-c:v", "libx264", "-pix_fmt", "yuv420p", source.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectErrorStream(true).start();
        assumeThat(make.waitFor(60, TimeUnit.SECONDS) && make.exitValue() == 0).isTrue();

        mockMvc.perform(upload(new MockMultipartFile("file", "walk.mov", "video/quicktime", Files.readAllBytes(source))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].kind").value("clip"));
        Files.deleteIfExists(source);

        Path saved;
        try (var listing = Files.list(mediaDir)) {
            saved = listing.findFirst().orElseThrow();
        }
        assertThat(saved.getFileName().toString()).endsWith(".mp4");
        assertThat(new String(Files.readAllBytes(saved), 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp");
    }

    @Test
    void aBrokenPngIs400() throws Exception {
        // Starts like a PNG, but there is no picture in it.
        byte[] broken = new byte[200];
        System.arraycopy(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 0, broken, 0, 8);

        mockMvc.perform(upload(new MockMultipartFile("file", "broken.png", "image/png", broken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unsupported_media"));

        try (var listing = Files.list(mediaDir)) {
            assertThat(listing.toList()).isEmpty();
        }
    }
}
