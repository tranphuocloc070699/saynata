package com.englishtranslation.backend.media;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the media of senses. Both methods answer: word id, then sense position (0 = first sense), then the media.
 * Senses without media are simply not in the maps.
 */
@Service
public class SenseMediaReader {

    private final JdbcClient jdbc;
    private final MediaProperties properties;

    public SenseMediaReader(JdbcClient jdbc, MediaProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** Backend words: word_lookup ids. */
    public Map<Long, Map<Integer, SenseMedia>> forBackendWords(Collection<Long> lookupIds) {
        return read("select x.word_lookup_id as word_id, x.sense_position as sense_position, m.file_name, m.kind,"
                + " m.photographer, m.page_url, m.license_note"
                + " from word_lookup_media x join media m on m.id = x.media_id"
                + " where x.word_lookup_id in (:ids)", lookupIds);
    }

    /** Bundled words: dictionary_word ids. */
    public Map<Long, Map<Integer, SenseMedia>> forBundledWords(Collection<Long> wordIds) {
        return read("select s.word_id as word_id, s.position as sense_position, m.file_name, m.kind,"
                + " m.photographer, m.page_url, m.license_note"
                + " from dictionary_sense s join media m on m.id = s.media_id"
                + " where s.word_id in (:ids)", wordIds);
    }

    private Map<Long, Map<Integer, SenseMedia>> read(String sql, Collection<Long> ids) {
        Map<Long, Map<Integer, SenseMedia>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Row> rows = jdbc.sql(sql).param("ids", ids).query((rs, n) -> new Row(
                rs.getLong("word_id"), rs.getInt("sense_position"),
                new SenseMedia(properties.urlFor(rs.getString("file_name")), rs.getString("kind"),
                        rs.getString("photographer"), rs.getString("page_url"), rs.getString("license_note")))).list();
        for (Row row : rows) {
            result.computeIfAbsent(row.wordId(), id -> new HashMap<>()).put(row.position(), row.media());
        }
        return result;
    }

    private record Row(long wordId, int position, SenseMedia media) {
    }
}
