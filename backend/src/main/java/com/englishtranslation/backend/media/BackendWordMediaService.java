package com.englishtranslation.backend.media;

import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import com.englishtranslation.backend.word.WordEntry;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gives the senses of a NEW backend word a Pixabay photo or clip, after the word was answered.
 *
 * Rules:
 * - Nouns and adjectives get one photo. Verbs get one short clip (a photo if Pixabay has no clip).
 * - The first 3 senses at most.
 * - A sense is claimed (a row in word_lookup_media) BEFORE Pixabay is asked. So Pixabay is asked once per sense,
 *   even when it found nothing or the download failed. To try again, an admin deletes the word.
 * - Nothing here may break the word answer: fetchInBackground runs on its own thread and catches every error.
 */
@Service
public class BackendWordMediaService {

    private static final Logger log = LoggerFactory.getLogger(BackendWordMediaService.class);
    private static final int MAX_SENSES = 3;

    private final PixabayClient pixabay;
    private final MediaStore store;
    private final MediaProperties properties;
    private final JdbcClient jdbc;

    public BackendWordMediaService(PixabayClient pixabay, MediaStore store, MediaProperties properties,
            JdbcClient jdbc) {
        this.pixabay = pixabay;
        this.store = store;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    /** Called by WordService after the new entry is saved. Returns at once; the work runs on the "media-" thread. */
    @Async("mediaExecutor")
    public void fetchInBackground(long lookupId, String word, WordEntry entry) {
        try {
            fetch(lookupId, word, entry);
        } catch (RuntimeException e) {
            log.warn("Media for '{}' failed: {}", word, e.toString());
        }
    }

    /** The same work, on the calling thread (tests call this one). */
    public void fetch(long lookupId, String word, WordEntry entry) {
        if (!properties.pixabayEnabled() || entry == null || entry.senses() == null) {
            return;
        }
        int count = Math.min(entry.senses().size(), MAX_SENSES);
        for (int position = 0; position < count; position++) {
            String kind = kindFor(entry.senses().get(position).pos());
            if (kind == null || !claim(lookupId, position)) {
                continue;
            }
            try {
                fetchOneSense(lookupId, position, word, kind);
            } catch (RuntimeException | IOException e) {
                // One bad sense must not stop the next one.
                log.warn("Media for '{}' sense {} failed: {}", word, position + 1, e.toString());
            }
        }
    }

    /** "clip" for verbs, "photo" for nouns and adjectives, null for the rest (adverbs, "the", ...). */
    static String kindFor(String pos) {
        String lower = pos == null ? "" : pos.toLowerCase();
        // Whole words only: "adverb" must not count as "verb".
        if (lower.matches(".*\\bverb\\b.*")) {
            return "clip";
        }
        if (lower.matches(".*\\b(noun|adjective)\\b.*")) {
            return "photo";
        }
        return null;
    }

    /** Makes the claim row. False when the sense was claimed before (so Pixabay is not asked again). */
    private boolean claim(long lookupId, int position) {
        try {
            jdbc.sql("insert into word_lookup_media (word_lookup_id, sense_position, created_at)"
                    + " values (:lookup, :position, :now)")
                    .param("lookup", lookupId).param("position", position).param("now", OffsetDateTime.now())
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private void fetchOneSense(long lookupId, int position, String word, String kind) throws IOException {
        Optional<PixabayHit> hit = Optional.empty();
        if (kind.equals("clip")) {
            hit = pixabay.findClip(word);
        }
        if (hit.isEmpty()) {
            hit = pixabay.findPhoto(word);
        }
        if (hit.isEmpty()) {
            log.info("Pixabay has no media for '{}' sense {}", word, position + 1);
            return;
        }
        long mediaId = store.save(word, position, hit.get());
        jdbc.sql("update word_lookup_media set media_id = :media where word_lookup_id = :lookup"
                + " and sense_position = :position")
                .param("media", mediaId).param("lookup", lookupId).param("position", position).update();
        log.info("Saved media {} for '{}' sense {}", mediaId, word, position + 1);
    }
}
