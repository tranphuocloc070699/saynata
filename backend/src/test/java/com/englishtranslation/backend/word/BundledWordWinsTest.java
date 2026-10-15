package com.englishtranslation.backend.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.dictionary.DictionarySense;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** TASK-58: when a word is in the bundled dictionary (dictionary_word), the bundled entry wins. */
@SpringBootTest(properties = "app.claude.api-key=test-key")
class BundledWordWinsTest {

    static final String EXAMPLES = "[{\"level\":\"easy\",\"text\":\"I run.\"},{\"level\":\"medium\",\"text\":\"She runs.\"},"
            + "{\"level\":\"harder\",\"text\":\"He ran to the bus.\"}]";
    static final String USAGE = "[{\"kind\":\"phrase\",\"pattern\":\"run away\",\"example\":\"The dog ran away.\"}]";

    @Autowired
    WordService service;
    @Autowired
    WordLookupRepository lookups;
    @Autowired
    DictionaryWordRepository bundled;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        // The database is shared with other test classes, so leave nothing behind.
        jdbc.update("delete from word_lookup_media");
        lookups.deleteAll();
        bundled.deleteAll();
        jdbc.update("delete from media where file_name like 'bundled_wins_%'");
    }

    private DictionaryWord saveBundledRun() {
        DictionaryWord word = new DictionaryWord("run", 5, "/rʌn/");
        DictionarySense sense = new DictionarySense(word, 0);
        sense.setText("verb", "A1", "to move fast on your legs", "chạy", EXAMPLES, USAGE);
        word.getSenses().add(sense);
        return bundled.save(word);
    }

    private long saveMedia(String fileName) {
        jdbc.update("insert into media (file_name, kind, photographer, page_url, license_note, created_at)"
                + " values (?, 'clip', 'Pat Maker', 'https://pixabay.com/x/1', 'Pixabay Content License', current_timestamp)",
                fileName);
        return jdbc.queryForObject("select id from media where file_name = ?", Long.class, fileName);
    }

    @Test
    void aBundledWordIsAnsweredFromPostgresWithoutClaudeAndWithoutSavingARow() {
        saveBundledRun();

        WordResponse response = service.lookUp("  Run ");

        assertThat(response.word()).isEqualTo("run");
        assertThat(response.ipa()).isEqualTo("/rʌn/");
        assertThat(response.cached()).isTrue();
        assertThat(response.entry().senses()).hasSize(1);
        WordEntryView.SenseView sense = response.entry().senses().get(0);
        assertThat(sense.simpleDefinition()).isEqualTo("to move fast on your legs");
        assertThat(sense.vietnamese()).isEqualTo("chạy");
        assertThat(sense.examples()).hasSize(3);
        assertThat(sense.usage()).extracting(WordEntry.Usage::pattern).containsExactly("run away");
        assertThat(sense.imageUrl()).isNull();
        verify(claude, never()).describeWord(anyString());
        verify(claude, never()).describeWordAgain(anyString(), anyString());
        assertThat(lookups.count()).isZero();
    }

    @Test
    void aBundledWordWinsOverAnOldBackendRowWithTheSameWord() {
        saveBundledRun();
        String oldEntry = "{\"senses\":[{\"pos\":\"verb\",\"cefr\":\"B1\",\"simpleDefinition\":\"OLD CLAUDE TEXT\","
                + "\"vietnamese\":\"x\",\"examples\":[],\"usage\":[]}]}";
        lookups.save(new WordLookup(
                com.englishtranslation.backend.cache.CacheKeys.key("word", "v7", "run"), "run", oldEntry, "m", "v7"));

        WordResponse response = service.lookUp("run");

        assertThat(response.entry().senses().get(0).simpleDefinition()).isEqualTo("to move fast on your legs");
    }

    @Test
    void theBundledMediaIsInTheAnswerAsImageUrl() {
        DictionaryWord run = saveBundledRun();
        long mediaId = saveMedia("bundled_wins_run_1.mp4");
        jdbc.update("update dictionary_sense set media_id = ? where word_id = ?", mediaId, run.getId());

        WordResponse response = service.lookUp("run");

        assertThat(response.entry().senses().get(0).imageUrl()).isEqualTo("https://api.loffy.me/media/bundled_wins_run_1.mp4");
    }

    @Test
    void aWordThatIsNotBundledStillGoesToClaude() {
        saveBundledRun();
        org.mockito.Mockito.when(claude.describeWord("serendipity")).thenReturn(new WordEntry(java.util.List.of()));

        service.lookUp("serendipity");

        verify(claude).describeWord("serendipity");
        assertThat(lookups.count()).isEqualTo(1);
    }

    // ---- migration V9 ----

    /** Runs the real migration file against the test database (the file ran once at start-up, on empty tables). */
    private void runMigration() throws IOException {
        String sql = new ClassPathResource("db/migration/V9__remove_backend_words_that_are_bundled.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
    }

    @Test
    void migrationDeletesBackendRowsOfBundledWordsWithTheirMediaLinksAndKeepsTheRest() throws IOException {
        saveBundledRun();
        String json = "{\"senses\":[]}";
        WordLookup run = lookups.save(new WordLookup("key-run", "run", json, "m", "v7"));
        WordLookup apple = lookups.save(new WordLookup("key-apple", "apple", json, "m", "v7", true));
        long mediaId = saveMedia("bundled_wins_run_old.webp");
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, media_id, created_at)"
                + " values (?, 0, ?, current_timestamp)", run.getId(), mediaId);
        jdbc.update("insert into word_lookup_media (word_lookup_id, sense_position, created_at)"
                + " values (?, 0, current_timestamp)", apple.getId());

        runMigration();

        assertThat(lookups.findAll()).extracting(WordLookup::getWord).containsExactly("apple");
        assertThat(jdbc.queryForObject("select count(*) from word_lookup_media", Integer.class)).isEqualTo(1);
        // The media row stays (the file stays on disk, a known gap).
        assertThat(jdbc.queryForObject("select count(*) from media where file_name = 'bundled_wins_run_old.webp'",
                Integer.class)).isEqualTo(1);
        assertThat(bundled.findWithSensesByLemma("run")).isPresent();
    }
}
