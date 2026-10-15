package com.englishtranslation.backend.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Deletes media that no sense uses any more (TASK-76): the row in the media table and the file in MEDIA_DIR.
 * Call it AFTER the change that unlinked the media has been committed. A media row that any bundled sense
 * (dictionary_sense) or backend sense (word_lookup_media) still points to is never deleted.
 */
@Service
public class MediaCleanup {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanup.class);

    private final JdbcClient jdbc;
    private final MediaProperties properties;

    public MediaCleanup(JdbcClient jdbc, MediaProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** Null ids are ignored. One failure is logged and does not stop the others. */
    public void deleteIfUnused(Collection<Long> mediaIds) {
        for (Long id : new LinkedHashSet<>(mediaIds)) {
            if (id == null) {
                continue;
            }
            try {
                deleteOne(id);
            } catch (RuntimeException e) {
                log.warn("Could not clean up media {}: {}", id, e.toString());
            }
        }
    }

    private void deleteOne(long id) {
        Optional<String> fileName = jdbc.sql("select file_name from media where id = :id").param("id", id)
                .query(String.class).optional();
        if (fileName.isEmpty()) {
            return;
        }
        // One statement checks and deletes, so a sense that links the row in the meantime keeps it.
        int deleted = jdbc.sql("delete from media where id = :id"
                + " and not exists (select 1 from dictionary_sense where media_id = :id)"
                + " and not exists (select 1 from word_lookup_media where media_id = :id)")
                .param("id", id).update();
        if (deleted > 0) {
            deleteFile(fileName.get());
        }
    }

    private void deleteFile(String fileName) {
        Path dir = Path.of(properties.dir()).toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            log.warn("Not deleting {}: it is outside the media folder", fileName);
            return;
        }
        try {
            if (Files.deleteIfExists(file)) {
                log.info("Deleted unused media file {}", fileName);
            } else {
                log.info("Media file {} was already gone", fileName);
            }
        } catch (IOException e) {
            log.warn("Could not delete media file {}: {}", fileName, e.toString());
        }
    }
}
