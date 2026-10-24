package com.englishtranslation.backend.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/words/{word}: imageUrl is an absolute address when the sense has media, and missing when it has none. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class WordImageUrlTest {

    static final String ENTRY = "{\"senses\":["
            + "{\"pos\":\"adjective\",\"cefr\":\"C1\",\"simpleDefinition\":\"not wanting to\",\"vietnamese\":\"miễn cưỡng\","
            + "\"examples\":[{\"level\":\"easy\",\"text\":\"She was reluctant.\"}],\"usage\":[]},"
            + "{\"pos\":\"adjective\",\"cefr\":\"C1\",\"simpleDefinition\":\"slow to react\",\"vietnamese\":\"chậm\","
            + "\"examples\":[{\"level\":\"easy\",\"text\":\"A reluctant engine.\"}],\"usage\":[]}]}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    ClaudeProperties claudeProperties;
    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    ClaudeService claude;

    long lookupId;

    @BeforeEach
    void setUp() {
        jdbc.sql("delete from word_lookup_media").update();
        jdbc.sql("update dictionary_sense set media_id = null").update();
        jdbc.sql("delete from media").update();
        lookups.deleteAll();
        String key = CacheKeys.key("word", claudeProperties.wordPromptVersion(), "reluctant");
        lookupId = lookups.save(new WordLookup(key, "reluctant", ENTRY, "test-model", "v7")).getId();
    }

    private void link(int position, String fileName, String kind) {
        jdbc.sql("insert into media (file_name, kind, photographer, page_url, license_note, created_at)"
                + " values (:file, :kind, 'Jane Photo', 'https://pixabay.com/photos/x-1/', 'Pixabay Content License', :now)")
                .param("file", fileName).param("kind", kind).param("now", OffsetDateTime.now()).update();
        long mediaId = jdbc.sql("select id from media where file_name = :file").param("file", fileName)
                .query(Long.class).single();
        jdbc.sql("insert into word_lookup_media (word_lookup_id, sense_position, media_id, created_at)"
                + " values (:lookup, :position, :media, :now)")
                .param("lookup", lookupId).param("position", position).param("media", mediaId)
                .param("now", OffsetDateTime.now()).update();
    }

    @Test
    void aSenseWithMediaHasAnAbsoluteImageUrlAndOtherFieldsAreUnchanged() throws Exception {
        link(0, "reluctant_1_ab12cd34.webp", "photo");

        mockMvc.perform(get("/api/words/reluctant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("reluctant"))
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.entry.senses[0].imageUrl").value("https://api.saynata.com/media/reluctant_1_ab12cd34.webp"))
                .andExpect(jsonPath("$.entry.senses[0].simpleDefinition").value("not wanting to"))
                .andExpect(jsonPath("$.entry.senses[0].vietnamese").value("miễn cưỡng"))
                .andExpect(jsonPath("$.entry.senses[0].examples[0].level").value("easy"));
    }

    @Test
    void aSenseWithoutMediaHasNoImageUrlField() throws Exception {
        link(0, "reluctant_1_ab12cd34.webp", "photo");

        mockMvc.perform(get("/api/words/reluctant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses[1].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.entry.senses[1].simpleDefinition").value("slow to react"));
    }

    @Test
    void aPhotoWithAKnownSizeSendsItsWidthAndHeight() throws Exception {
        link(0, "reluctant_1_ab12cd34.webp", "photo");
        link(1, "reluctant_2_ef56ab78.mp4", "clip");
        jdbc.sql("update media set width = 320, height = 470 where file_name = 'reluctant_1_ab12cd34.webp'").update();

        mockMvc.perform(get("/api/words/reluctant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses[0].imageWidth").value(320))
                .andExpect(jsonPath("$.entry.senses[0].imageHeight").value(470))
                .andExpect(jsonPath("$.entry.senses[1].imageUrl").exists())
                .andExpect(jsonPath("$.entry.senses[1].imageWidth").doesNotExist())
                .andExpect(jsonPath("$.entry.senses[1].imageHeight").doesNotExist());
    }

    @Test
    void aWordWithNoMediaAtAllLooksLikeBefore() throws Exception {
        mockMvc.perform(get("/api/words/reluctant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.senses.length()").value(2))
                .andExpect(jsonPath("$.entry.senses[0].imageUrl").doesNotExist());
    }
}
