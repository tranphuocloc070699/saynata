package com.englishtranslation.backend.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.prefill.PrefillItem;
import com.englishtranslation.backend.prefill.PrefillItemRepository;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.englishtranslation.backend.admin.LoginRateLimiter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** The Sources API (TASK-108): ADMIN only, upload limits, filtering against the database, and queueing. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AdminSourcesTest {

    static final String BOOK = "Alice wandered by the river. The ephemeral giraffe wandered too.\n"
            + "She gave up the search. Then Alice laughed at the ephemeral giraffe.";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    PrefillItemRepository queue;
    @Autowired
    SourceUploadRepository uploads;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    LoginRateLimiter loginLimiter;

    Cookie cookie;

    @BeforeEach
    void setUp() throws Exception {
        uploads.deleteAll();
        queue.deleteAll();
        bundled.deleteAll();
        users.deleteAll();
        users.save(new User("admin@example.com", passwordEncoder.encode("correct-horse-battery"), "Admin", Role.ADMIN));
        loginLimiter.reset();
        cookie = mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"admin@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("saynata_admin");
    }

    private MockMultipartHttpServletRequestBuilder upload(String fileName, byte[] bytes, String title) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/sources")
                .file(new MockMultipartFile("file", fileName, "text/plain", bytes))
                .cookie(cookie).header("X-Requested-With", "XMLHttpRequest");
        if (title != null) {
            request.param("title", title);
        }
        return request;
    }

    private MvcResult uploadBook() throws Exception {
        bundled.save(new DictionaryWord("river", 100, null));
        queue.save(new PrefillItem("word", "wander", "", "scowl-70", 300, Instant.now()));
        return mockMvc.perform(upload("alice.txt", BOOK.getBytes(StandardCharsets.UTF_8), "Alice Test"))
                .andExpect(status().isCreated()).andReturn();
    }

    private long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));
    }

    @Test
    void bothCallsNeedAnAdminLogin() throws Exception {
        mockMvc.perform(multipart("/api/admin/sources")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes()))
                .header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/sources/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/sources/1/queue").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUploadWithoutTheCustomHeaderIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/admin/sources")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes())).cookie(cookie))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadFindsNewWordsAndPhrasesAndSkipsKnownOnesAndNames() throws Exception {
        MvcResult result = uploadBook();

        mockMvc.perform(get("/api/admin/sources/" + idOf(result)).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alice Test"))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.counts.namesSkipped").value(1))
                .andExpect(jsonPath("$.candidates[?(@.kind=='word')].text", hasItem("ephemeral")))
                .andExpect(jsonPath("$.candidates[?(@.kind=='word')].text", hasItem("giraffe")))
                .andExpect(jsonPath("$.candidates[?(@.kind=='word')].text", not(hasItem("river")))) // bundled
                .andExpect(jsonPath("$.candidates[?(@.kind=='word')].text", not(hasItem("wander")))) // queued
                .andExpect(jsonPath("$.candidates[?(@.kind=='word')].text", not(hasItem("alice")))) // a name
                .andExpect(jsonPath("$.candidates[?(@.kind=='name')].text", hasItem("alice")))
                .andExpect(jsonPath("$.candidates[?(@.kind=='phrase')].text", hasItem("give up")))
                .andExpect(jsonPath("$.candidates[?(@.text=='ephemeral')].times").value(2))
                .andExpect(jsonPath("$.candidates[?(@.text=='ephemeral')].useful").value(false));
    }

    @Test
    void theTextIsNeverStoredOnlyShortSnippets() throws Exception {
        uploadBook();

        SourceUpload saved = uploads.findAll().get(0);
        assertThat(saved.getCandidates()).doesNotContain(BOOK).doesNotContain("\n\n");
        for (String line : saved.getCandidates().split("\n")) {
            String snippet = SourceCandidate.fromLine(line).snippet();
            assertThat(snippet.isEmpty() ? 0 : snippet.split(" ").length).isLessThanOrEqualTo(12);
        }
    }

    @Test
    void uploadLimitsAndBadFiles() throws Exception {
        byte[] big = new byte[(int) SourceService.MAX_BYTES + 1];
        Arrays.fill(big, (byte) 'a');
        mockMvc.perform(upload("big.txt", big, "Big")).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("file_too_large"));

        byte[] ok = new byte[(int) SourceService.MAX_BYTES];
        Arrays.fill(ok, (byte) ' ');
        mockMvc.perform(upload("exact.txt", ok, "Exact")).andExpect(status().isCreated());

        mockMvc.perform(upload("book.pdf", "hello".getBytes(), "Pdf")).andExpect(status().isBadRequest());
        mockMvc.perform(upload("empty.txt", new byte[0], "Empty")).andExpect(status().isBadRequest());
        mockMvc.perform(upload("binary.txt", new byte[] {'a', 0, 'b'}, "Binary")).andExpect(status().isBadRequest());
        mockMvc.perform(upload(".txt", "hello".getBytes(), "  ")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/sources").contentType(MediaType.MULTIPART_FORM_DATA)
                .cookie(cookie).header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isBadRequest());
    }

    @Test
    void theTitleDefaultsToTheFileName() throws Exception {
        mockMvc.perform(upload("moby-dick.md", "The ephemeral whale.".getBytes(), null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("moby-dick"));
    }

    @Test
    void queueAddsThePickedItemsWithTheBookSourceAndSkipsQueuedOnes() throws Exception {
        long id = idOf(uploadBook());
        queue.save(new PrefillItem("word", "giraffe", "", "scowl-70", 300, Instant.now()));
        // "giraffe" was not queued when the upload was read, but is now: it is skipped, not doubled.

        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"kind\":\"word\",\"text\":\"ephemeral\"},{\"kind\":\"word\",\"text\":\"giraffe\"},"
                        + "{\"kind\":\"phrase\",\"text\":\"give up\"},{\"kind\":\"word\",\"text\":\"ephemeral\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(2))
                .andExpect(jsonPath("$.alreadyQueued").value(1))
                .andExpect(jsonPath("$.source").value("book:Alice Test"));

        PrefillItem word = queue.findByKindAndTextAndTargetLang("word", "ephemeral", "").orElseThrow();
        assertThat(word.getSource()).isEqualTo("book:Alice Test");
        assertThat(word.getStatus()).isEqualTo("pending");
        assertThat(word.getPriority()).isGreaterThan(0);
        PrefillItem phrase = queue.findByKindAndTextAndTargetLang("sentence", "give up", "vi").orElseThrow();
        assertThat(phrase.getSource()).isEqualTo("book:Alice Test");
        assertThat(queue.findByKindAndTextAndTargetLang("word", "giraffe", "").orElseThrow().getSource())
                .isEqualTo("scowl-70");
    }

    @Test
    void afterQueueingTheSnippetsAreGoneAndASecondQueueIs409() throws Exception {
        long id = idOf(uploadBook());
        String body = "{\"items\":[{\"kind\":\"word\",\"text\":\"ephemeral\"}]}";

        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/sources/" + id).cookie(cookie))
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.queuedCount").value(1))
                .andExpect(jsonPath("$.candidates[?(@.snippet != '')]").isEmpty());
        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_queued"));
    }

    @Test
    void queueRefusesItemsThatAreNotInTheUploadNamesAndEmptyPicks() throws Exception {
        long id = idOf(uploadBook());

        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"kind\":\"word\",\"text\":\"zebra\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"kind\":\"name\",\"text\":\"alice\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/sources/" + id + "/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/sources/99999/queue").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"kind\":\"word\",\"text\":\"a\"}]}"))
                .andExpect(status().isNotFound());
        assertThat(queue.count()).isEqualTo(1); // only the "wander" row of the test setup
    }
}
