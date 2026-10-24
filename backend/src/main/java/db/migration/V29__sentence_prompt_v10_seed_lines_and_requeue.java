package db.migration;

import com.englishtranslation.backend.cache.CacheKeys;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * TASK-106.6: sentence prompt v10 writes only the English part, and the sentence cache key has no language.
 * <ol>
 * <li>Every Vietnamese translation in a cached sentence row becomes a language line in {@code line_translation}
 * (source seed, line prompt version v1), so Vietnamese sentences keep their line for free. Only text that is
 * already normalized the way Java does it ({@code CacheKeys.normalizeSentence}) is copied, and the hash is the same
 * {@code CacheKeys.key} the line service uses. The newest row wins when one text has several; a line that already
 * exists is never replaced.</li>
 * <li>Every sentence the prefill job answered goes back to pending, like V26 did for v9: the v9 answers are not read
 * any more, so the next runs write the English part with v10.</li>
 * </ol>
 * A Java migration for the same reason as V18: the hash is computed by Java, and Postgres and H2 (the tests) have
 * different hash functions.
 */
public class V29__sentence_prompt_v10_seed_lines_and_requeue extends BaseJavaMigration {

    static final String LINE_PROMPT_VERSION = "v1";

    @Override
    public void migrate(Context context) throws Exception {
        seedVietnameseLines(context);
        try (Statement requeue = context.getConnection().createStatement()) {
            requeue.executeUpdate("UPDATE prefill_item SET status = 'pending', attempts = 0, last_error = NULL, "
                    + "updated_at = CURRENT_TIMESTAMP WHERE kind = 'sentence' AND status = 'done'");
        }
    }

    private void seedVietnameseLines(Context context) throws Exception {
        // text hash -> {text, translation}; rows come newest first, so the first one stays.
        Map<String, String[]> lines = new LinkedHashMap<>();
        try (Statement select = context.getConnection().createStatement();
                ResultSet rows = select.executeQuery("SELECT source_text, translation FROM sentence_translation "
                        + "WHERE target_lang = 'vi' ORDER BY created_at DESC, id DESC")) {
            while (rows.next()) {
                String text = rows.getString("source_text");
                String translation = rows.getString("translation");
                if (text == null || translation == null || translation.isBlank()
                        || text.isEmpty() || !text.equals(CacheKeys.normalizeSentence(text))) {
                    continue;
                }
                lines.putIfAbsent(CacheKeys.key(text), new String[] { text, translation });
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement exists = context.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM line_translation WHERE text_hash = ? AND target_lang = 'vi' AND prompt_version = ?");
                PreparedStatement insert = context.getConnection().prepareStatement(
                        "INSERT INTO line_translation (text_hash, text, target_lang, prompt_version, translation, "
                                + "source, created_at) VALUES (?, ?, 'vi', ?, ?, 'seed', ?)")) {
            for (Map.Entry<String, String[]> line : lines.entrySet()) {
                exists.setString(1, line.getKey());
                exists.setString(2, LINE_PROMPT_VERSION);
                try (ResultSet found = exists.executeQuery()) {
                    found.next();
                    if (found.getInt(1) > 0) {
                        continue;
                    }
                }
                insert.setString(1, line.getKey());
                insert.setString(2, line.getValue()[0]);
                insert.setString(3, LINE_PROMPT_VERSION);
                insert.setString(4, line.getValue()[1]);
                insert.setTimestamp(5, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
