package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * TASK-83.4: indexes for GET /api/suggest, which looks for text that starts with what the user typed.
 *
 * A Java migration, not .sql, because Postgres and H2 (the tests) need different SQL. In Postgres a plain index
 * on a text column is not used for {@code LIKE 'app%'} unless the database collation is "C", so the indexes use
 * {@code text_pattern_ops}, and the phrase index is on {@code lower(source_text)}. H2 has neither; it gets plain
 * indexes (its tables in the tests are tiny anyway).
 */
public class V19__add_prefix_indexes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        boolean postgres = context.getConnection().getMetaData().getDatabaseProductName()
                .toLowerCase().contains("postgres");
        try (Statement statement = context.getConnection().createStatement()) {
            if (postgres) {
                statement.execute("CREATE INDEX idx_dictionary_word_lemma_prefix "
                        + "ON dictionary_word (lemma text_pattern_ops)");
                statement.execute("CREATE INDEX idx_word_lookup_word_prefix "
                        + "ON word_lookup (word text_pattern_ops)");
                statement.execute("CREATE INDEX idx_sentence_translation_text_prefix "
                        + "ON sentence_translation (lower(source_text) text_pattern_ops)");
            } else {
                statement.execute("CREATE INDEX idx_word_lookup_word_prefix ON word_lookup (word)");
                statement.execute("CREATE INDEX idx_sentence_translation_text_prefix ON sentence_translation (source_text)");
            }
        }
    }
}
