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
                + " m.photographer, m.page_url, m.license_note, m.source"
                + " from word_lookup_media x join media m on m.id = x.media_id"
                + " where x.word_lookup_id in (:ids)", lookupIds);
    }

    /** Bundled words: dictionary_word ids. */
    public Map<Long, Map<Integer, SenseMedia>> forBundledWords(Collection<Long> wordIds) {
        return read("select s.word_id as word_id, s.position as sense_position, m.file_name, m.kind,"
                + " m.photographer, m.page_url, m.license_note, m.source"
                + " from dictionary_sense s join media m on m.id = s.media_id"
                + " where s.word_id in (:ids)", wordIds);
    }

    /** Backend words: which senses the admin marked (status "needs_ai"). Word id, then sense position, then status. */
    public Map<Long, Map<Integer, String>> statusForBackendWords(Collection<Long> lookupIds) {
        return readStatus("select word_lookup_id as word_id, sense_position, media_status from word_lookup_media"
                + " where media_status is not null and word_lookup_id in (:ids)", lookupIds);
    }

    /** Bundled words: the same, from dictionary_sense. */
    public Map<Long, Map<Integer, String>> statusForBundledWords(Collection<Long> wordIds) {
        return readStatus("select word_id, position as sense_position, media_status from dictionary_sense"
                + " where media_status is not null and word_id in (:ids)", wordIds);
    }

    private Map<Long, Map<Integer, String>> readStatus(String sql, Collection<Long> ids) {
        Map<Long, Map<Integer, String>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.sql(sql).param("ids", ids).query((rs, n) -> {
            result.computeIfAbsent(rs.getLong("word_id"), id -> new HashMap<>())
                    .put(rs.getInt("sense_position"), rs.getString("media_status"));
            return null;
        }).list();
        return result;
    }

    private Map<Long, Map<Integer, SenseMedia>> read(String sql, Collection<Long> ids) {
        Map<Long, Map<Integer, SenseMedia>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<Row> rows = jdbc.sql(sql).param("ids", ids).query((rs, n) -> new Row(
                rs.getLong("word_id"), rs.getInt("sense_position"),
                new SenseMedia(properties.urlFor(rs.getString("file_name")), rs.getString("kind"),
                        rs.getString("photographer"), rs.getString("page_url"), rs.getString("license_note"), rs.getString("source")))).list();
        for (Row row : rows) {
            result.computeIfAbsent(row.wordId(), id -> new HashMap<>()).put(row.position(), row.media());
        }
        return result;
    }

    private record Row(long wordId, int position, SenseMedia media) {
    }
}
