package com.englishtranslation.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishtranslation.backend.cache.CacheKeys;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Flyway V18 (TASK-83.3) fills match_key for rows that were saved before the column existed, and V24 computes it again
 * after the loose form learned curly quotes, … and commas.
 */
class SentenceMatchKeyMigrationTest {

    @Test
    void oldRowsGetTheSameKeyJavaGivesNewRows() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:match_key_migration;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("16").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insert(jdbc, "k1", "How are you?", "vi", "v6");
        insert(jdbc, "k2", "how are you", "ja", "v5");
        insert(jdbc, "k3", "US", "vi", "v6");
        insert(jdbc, "k4", "us", "vi", "v6");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(matchKey(jdbc, "k1")).isEqualTo(CacheKeys.key("sentence-loose", "v6", "vi", "how are you"));
        assertThat(matchKey(jdbc, "k2")).isEqualTo(CacheKeys.key("sentence-loose", "v5", "ja", "how are you"));
        // Two exact texts, one loose key: the index must not be unique.
        assertThat(matchKey(jdbc, "k3")).isEqualTo(matchKey(jdbc, "k4"));
        assertThat(jdbc.queryForObject(
                "select count(*) from sentence_translation where match_key is null", Integer.class)).isZero();
    }

    @Test
    void v24GivesOldRowsTheNewLooseKey() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:match_key_v24;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("23").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // The key V18 gave this row before: the curly apostrophe and the "…" were part of the loose text.
        String stale = CacheKeys.key("sentence-loose", "v6", "vi", "i\u2019m fine\u2026");
        jdbc.update("insert into sentence_translation (cache_key, source_text, translation, model, prompt_version,"
                + " created_at, target_lang, match_key) values ('k1', 'I\u2019m fine\u2026', 'x', 'm', 'v6',"
                + " current_timestamp, 'vi', ?)", stale);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(matchKey(jdbc, "k1"))
                .isEqualTo(CacheKeys.key("sentence-loose", "v6", "vi", "i'm fine"))
                .isEqualTo(CacheKeys.sentenceMatchKey("v6", "vi", "I'm fine"));
        assertThat(jdbc.queryForObject("select source_text from sentence_translation where cache_key = 'k1'",
                String.class)).isEqualTo("I\u2019m fine\u2026");
    }

    private static void insert(JdbcTemplate jdbc, String cacheKey, String text, String lang, String version) {
        jdbc.update("insert into sentence_translation (cache_key, source_text, translation, model, prompt_version,"
                + " created_at, target_lang) values (?, ?, 'x', 'm', ?, current_timestamp, ?)",
                cacheKey, text, version, lang);
    }

    private static String matchKey(JdbcTemplate jdbc, String cacheKey) {
        return jdbc.queryForObject("select match_key from sentence_translation where cache_key = ?", String.class, cacheKey);
    }
}
