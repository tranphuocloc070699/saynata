package com.englishtranslation.backend.aipicture;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** The table ai_picture_job (Flyway V21): one row per AI picture the admin asked for. Plain SQL, like the media code. */
@Repository
public class AiPictureJobs {

    public static final String QUEUED = "queued";
    public static final String DRAWING = "drawing";
    public static final String DONE = "done";
    public static final String FAILED = "failed";

    /** One job row. */
    public record Row(long id, String wordId, int position, String prompt, String status, String error, Long mediaId,
            Instant createdAt, Instant finishedAt) {
    }

    private static final String COLUMNS = "id, word_id, position, prompt, status, error, media_id, created_at, finished_at";

    private final JdbcClient jdbc;

    public AiPictureJobs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String wordId, int position, String prompt, String promptSource, String createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("insert into ai_picture_job (word_id, position, prompt, prompt_source, status, created_by, created_at)"
                + " values (:word, :position, :prompt, :source, 'queued', :by, :now)")
                .param("word", wordId).param("position", position).param("prompt", prompt)
                .param("source", promptSource).param("by", createdBy).param("now", OffsetDateTime.now())
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public Optional<Row> find(long id) {
        return jdbc.sql("select " + COLUMNS + " from ai_picture_job where id = :id").param("id", id)
                .query((rs, n) -> row(rs)).optional();
    }

    /** The newest job of the sense, whatever its status. */
    public Optional<Row> latest(String wordId, int position) {
        return jdbc.sql("select " + COLUMNS + " from ai_picture_job where word_id = :word and position = :position"
                + " order by created_at desc, id desc limit 1")
                .param("word", wordId).param("position", position).query((rs, n) -> row(rs)).optional();
    }

    /** True while a job of this sense is queued or drawing. */
    public boolean hasRunning(String wordId, int position) {
        return jdbc.sql("select count(*) from ai_picture_job where word_id = :word and position = :position"
                + " and status in ('queued', 'drawing')").param("word", wordId).param("position", position)
                .query(Long.class).single() > 0;
    }

    /** How many jobs were started since {@code from} (the start of the UTC day), whatever came of them. */
    public int countSince(Instant from) {
        return jdbc.sql("select count(*) from ai_picture_job where created_at >= :from")
                .param("from", OffsetDateTime.ofInstant(from, java.time.ZoneOffset.UTC)).query(Long.class).single().intValue();
    }

    public void markDrawing(long id) {
        jdbc.sql("update ai_picture_job set status = 'drawing', started_at = :now where id = :id")
                .param("now", OffsetDateTime.now()).param("id", id).update();
    }

    /** Runs inside the transaction that links the picture, so the job is "done" only if the link happened. */
    public void markDone(long id, long mediaId, Long oldMediaId) {
        jdbc.sql("update ai_picture_job set status = 'done', error = null, media_id = :media, old_media_id = :old,"
                + " finished_at = :now where id = :id")
                .param("media", mediaId).param("old", oldMediaId).param("now", OffsetDateTime.now()).param("id", id)
                .update();
    }

    public void markFailed(long id, String error) {
        jdbc.sql("update ai_picture_job set status = 'failed', error = :error, finished_at = :now where id = :id")
                .param("error", error).param("now", OffsetDateTime.now()).param("id", id).update();
    }

    /** At start-up: a job that was queued or drawing when the server stopped will never finish. Returns how many. */
    public int failUnfinished(String error) {
        return jdbc.sql("update ai_picture_job set status = 'failed', error = :error, finished_at = :now"
                + " where status in ('queued', 'drawing')").param("error", error).param("now", OffsetDateTime.now())
                .update();
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new Row(rs.getLong("id"), rs.getString("word_id"), rs.getInt("position"), rs.getString("prompt"),
                rs.getString("status"), rs.getString("error"), (Long) rs.getObject("media_id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), finished == null ? null : finished.toInstant());
    }
}
