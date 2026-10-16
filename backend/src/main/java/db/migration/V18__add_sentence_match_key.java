package db.migration;

import com.englishtranslation.backend.cache.CacheKeys;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * TASK-83.3: a loose key on sentence_translation, so "how are you" finds a saved "How are you?".
 *
 * This is a Java migration, not .sql, on purpose: the key is a sha256 of text that Java normalises
 * (lowercase, final punctuation removed). Postgres and H2 (the tests) have different hash functions, and a
 * second copy of the normaliser in SQL could drift. Here the old rows get their key from
 * {@link CacheKeys#sentenceMatchKey}, the same method the app uses for new rows.
 *
 * The prompt version used is the one saved in the row, like the exact cache_key does.
 */
public class V18__add_sentence_match_key extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE sentence_translation ADD COLUMN match_key VARCHAR(64)");
        }

        try (Statement select = context.getConnection().createStatement();
                ResultSet rows = select.executeQuery(
                        "SELECT id, prompt_version, target_lang, source_text FROM sentence_translation");
                PreparedStatement update = context.getConnection()
                        .prepareStatement("UPDATE sentence_translation SET match_key = ? WHERE id = ?")) {
            while (rows.next()) {
                update.setString(1, CacheKeys.sentenceMatchKey(
                        rows.getString("prompt_version"), rows.getString("target_lang"), rows.getString("source_text")));
                update.setLong(2, rows.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE sentence_translation ALTER COLUMN match_key SET NOT NULL");
            // Not unique: "US" and "us" are two exact texts with one loose key.
            statement.execute("CREATE INDEX idx_sentence_translation_match_key ON sentence_translation (match_key)");
        }
    }
}
