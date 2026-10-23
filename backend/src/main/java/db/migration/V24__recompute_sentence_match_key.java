package db.migration;

import com.englishtranslation.backend.cache.CacheKeys;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * TASK-83.3: the loose sentence form now also makes curly quotes straight and drops a final … or comma
 * ("I’m fine…" = "i'm fine"), so every saved match_key is computed again with
 * {@link CacheKeys#sentenceMatchKey}. A Java migration for the same reason as V18: the key is a sha256 of text that
 * Java normalises, and Postgres and H2 (the tests) have different hash functions. Only rows whose key changes are
 * written.
 */
public class V24__recompute_sentence_match_key extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement select = context.getConnection().createStatement();
                ResultSet rows = select.executeQuery(
                        "SELECT id, prompt_version, target_lang, source_text, match_key FROM sentence_translation");
                PreparedStatement update = context.getConnection()
                        .prepareStatement("UPDATE sentence_translation SET match_key = ? WHERE id = ?")) {
            while (rows.next()) {
                String matchKey = CacheKeys.sentenceMatchKey(
                        rows.getString("prompt_version"), rows.getString("target_lang"), rows.getString("source_text"));
                if (!matchKey.equals(rows.getString("match_key"))) {
                    update.setString(1, matchKey);
                    update.setLong(2, rows.getLong("id"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }
}
