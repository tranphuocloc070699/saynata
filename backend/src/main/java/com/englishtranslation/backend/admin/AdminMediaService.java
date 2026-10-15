package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.media.HttpPixabayClient;
import com.englishtranslation.backend.media.MediaProperties;
import com.englishtranslation.backend.media.MediaStore;
import com.englishtranslation.backend.media.PixabayBusyException;
import com.englishtranslation.backend.media.PixabayClient;
import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import com.englishtranslation.backend.media.SenseMedia;
import com.englishtranslation.backend.word.WordEntry;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The admin fixes the picture of one sense (TASK-52): search Pixabay, pick another result, remove the picture, or
 * mark the sense for an AI image. It works for bundled words (dictionary_sense.media_id) and for backend words
 * (word_lookup_media). A sense is named by the word id and the sense position, for example "b-12" and 0.
 */
@Service
public class AdminMediaService {

    private static final Logger log = LoggerFactory.getLogger(AdminMediaService.class);

    static final int MAX_RESULTS = 6;
    static final String NEEDS_AI = "needs_ai";
    static final String BUSY_MESSAGE = "Pixabay is busy right now. Try again in a minute.";

    /** One Pixabay result. previewUrl is for the small picture in the admin; downloadUrl is what we fetch if it is chosen. */
    public record SearchResult(String kind, long pixabayId, String previewUrl, String downloadUrl, String photographer,
            String pageUrl) {
    }

    /** The search words that were used (the sense's own when the admin sent none) and the results. */
    public record SearchResponse(String query, List<SearchResult> results) {
    }

    /** The result the admin chose, as the search gave it. */
    public record ChosenResult(String kind, long pixabayId, String downloadUrl, String photographer, String pageUrl) {
    }

    /** A sense that waits for an AI image. */
    public record MarkedSense(String wordId, String word, int position, String pos, String simpleDefinition,
            SenseMedia media) {
    }

    /** What the admin needs to know about one sense before it is changed. */
    private record Sense(String word, String defaultQuery) {
    }

    private final PixabayClient pixabay;
    private final MediaStore store;
    private final MediaProperties properties;
    private final MediaSearchRateLimiter rateLimiter;
    private final AdminWordService words;
    private final JdbcClient jdbc;

    public AdminMediaService(PixabayClient pixabay, MediaStore store, MediaProperties properties,
            MediaSearchRateLimiter rateLimiter, AdminWordService words, JdbcClient jdbc) {
        this.pixabay = pixabay;
        this.store = store;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.words = words;
        this.jdbc = jdbc;
    }

    // ---- search ----

    /** Up to 6 Pixabay results. {@code query} may be blank: then the sense's image_query (or the word) is used. */
    public SearchResponse search(String wordId, int position, String query, String admin) {
        AdminWordId id = AdminWordId.parse(wordId);
        Sense sense = findSense(id, position);
        requirePixabay();
        if (!rateLimiter.tryAcquire(admin)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many searches. Wait a few minutes.");
        }
        String searchWords = query == null || query.isBlank() ? sense.defaultQuery() : query.trim();
        if (searchWords.length() > 100) {
            throw new InvalidAdminRequestException("The search words must be 100 characters or fewer.");
        }
        List<PixabayHit> hits = fromPixabay(() -> pixabay.search(searchWords, MAX_RESULTS));
        List<SearchResult> results = new ArrayList<>();
        for (PixabayHit hit : hits) {
            results.add(new SearchResult(hit.kind(), hit.id(), hit.previewUrl(), hit.downloadUrl(), hit.photographer(),
                    hit.pageUrl()));
        }
        return new SearchResponse(searchWords, results);
    }

    // ---- change ----

    /** Downloads the chosen result, converts it, and makes it the picture of the sense. Returns the changed word. */
    public AdminWord replace(String wordId, int position, ChosenResult chosen) {
        AdminWordId id = AdminWordId.parse(wordId);
        Sense sense = findSense(id, position);
        PixabayHit hit = checked(chosen);
        requirePixabay();

        long mediaId;
        try {
            mediaId = store.save(sense.word(), position, hit);
        } catch (PixabayBusyException e) {
            log.warn("Pixabay is busy, could not save the chosen media for {} sense {}: {}", wordId, position + 1, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSY_MESSAGE);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not save the chosen media for {} sense {}: {}", wordId, position + 1, e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not download or convert that file. Try another one.");
        }
        link(id, position, mediaId, null);
        if (id.bundled()) {
            // A sense that was "no image" has a picture again.
            jdbc.sql("update dictionary_sense set image_type = 'photo' where word_id = :word and position = :position"
                    + " and image_type = 'none'").param("word", id.number()).param("position", position).update();
        }
        return words.get(id.text());
    }

    /** The sense has no picture afterwards. For a backend word the row stays, so the background job does not fetch again. */
    public AdminWord clear(String wordId, int position) {
        AdminWordId id = AdminWordId.parse(wordId);
        findSense(id, position);
        link(id, position, null, null);
        if (id.bundled()) {
            // Tells the pipeline too: this sense wants no image.
            jdbc.sql("update dictionary_sense set image_type = 'none' where word_id = :word and position = :position")
                    .param("word", id.number()).param("position", position).update();
        }
        return words.get(id.text());
    }

    /** media_status = needs_ai. The current picture stays until an AI image replaces it (TASK-53). */
    public AdminWord markForAi(String wordId, int position) {
        AdminWordId id = AdminWordId.parse(wordId);
        findSense(id, position);
        if (id.bundled()) {
            jdbc.sql("update dictionary_sense set media_status = :status where word_id = :word and position = :position")
                    .param("status", NEEDS_AI).param("word", id.number()).param("position", position).update();
        } else {
            ensureBackendRow(id.number(), position);
            jdbc.sql("update word_lookup_media set media_status = :status where word_lookup_id = :word"
                    + " and sense_position = :position")
                    .param("status", NEEDS_AI).param("word", id.number()).param("position", position).update();
        }
        return words.get(id.text());
    }

    // ---- list ----

    /** All senses with the given status (only "needs_ai" exists today). */
    public List<MarkedSense> listByStatus(String status) {
        if (!NEEDS_AI.equals(status)) {
            throw new InvalidAdminRequestException("status must be: " + NEEDS_AI + ".");
        }
        List<MarkedSense> marked = new ArrayList<>();
        for (Row row : jdbc.sql("select 'b-' || cast(word_id as varchar(20)) as word_id, position as sense_position"
                + " from dictionary_sense where media_status = :status order by word_id, position")
                .param("status", status).query((rs, n) -> new Row(rs.getString("word_id"), rs.getInt("sense_position"))).list()) {
            addMarked(marked, row);
        }
        for (Row row : jdbc.sql("select 'w-' || cast(word_lookup_id as varchar(20)) as word_id, sense_position"
                + " from word_lookup_media where media_status = :status order by word_lookup_id, sense_position")
                .param("status", status).query((rs, n) -> new Row(rs.getString("word_id"), rs.getInt("sense_position"))).list()) {
            addMarked(marked, row);
        }
        return marked;
    }

    private record Row(String wordId, int position) {
    }

    private void addMarked(List<MarkedSense> marked, Row row) {
        AdminWord word = words.get(row.wordId());
        WordEntry entry = word.entry();
        if (entry == null || row.position() >= entry.senses().size()) {
            return;
        }
        WordEntry.Sense sense = entry.senses().get(row.position());
        SenseMedia media = row.position() < word.media().size() ? word.media().get(row.position()) : null;
        marked.add(new MarkedSense(word.id(), word.word(), row.position(), sense.pos(), sense.simpleDefinition(), media));
    }

    // ---- helpers ----

    /** Sets media_id (null = no media) and clears the status. */
    private void link(AdminWordId id, int position, Long mediaId, String status) {
        if (id.bundled()) {
            jdbc.sql("update dictionary_sense set media_id = :media, media_status = :status"
                    + " where word_id = :word and position = :position")
                    .param("media", mediaId).param("status", status).param("word", id.number())
                    .param("position", position).update();
        } else {
            ensureBackendRow(id.number(), position);
            jdbc.sql("update word_lookup_media set media_id = :media, media_status = :status"
                    + " where word_lookup_id = :word and sense_position = :position")
                    .param("media", mediaId).param("status", status).param("word", id.number())
                    .param("position", position).update();
        }
    }

    /** Makes the word_lookup_media row of the sense if it is not there yet (the background job makes it only for 3 senses). */
    private void ensureBackendRow(long lookupId, int position) {
        boolean exists = jdbc.sql("select count(*) from word_lookup_media where word_lookup_id = :word"
                + " and sense_position = :position").param("word", lookupId).param("position", position)
                .query(Long.class).single() > 0;
        if (!exists) {
            jdbc.sql("insert into word_lookup_media (word_lookup_id, sense_position, created_at)"
                    + " values (:word, :position, :now)")
                    .param("word", lookupId).param("position", position).param("now", OffsetDateTime.now()).update();
        }
    }

    /** The sense must exist, else 404. */
    private Sense findSense(AdminWordId id, int position) {
        if (position < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sense " + position + ".");
        }
        if (id.bundled()) {
            List<Sense> found = jdbc.sql("select w.lemma, s.image_query from dictionary_sense s"
                    + " join dictionary_word w on w.id = s.word_id where s.word_id = :word and s.position = :position")
                    .param("word", id.number()).param("position", position)
                    .query((rs, n) -> new Sense(rs.getString("lemma"), firstNonBlank(rs.getString("image_query"), rs.getString("lemma"))))
                    .list();
            if (found.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sense " + (position + 1) + " for word " + id.text() + ".");
            }
            return found.get(0);
        }
        AdminWord word = words.get(id.text()); // 404 when the word is missing
        if (word.entry() == null || position >= word.entry().senses().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No sense " + (position + 1) + " for word " + id.text() + ".");
        }
        return new Sense(word.word(), word.word());
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private void requirePixabay() {
        if (!properties.pixabayEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pixabay is not set up. Set PIXABAY_API_KEY on the server.");
        }
    }

    private List<PixabayHit> fromPixabay(java.util.function.Supplier<List<PixabayHit>> call) {
        try {
            return call.get();
        } catch (PixabayBusyException e) {
            log.warn("Pixabay search failed: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, BUSY_MESSAGE);
        } catch (RuntimeException e) {
            log.warn("Pixabay search failed: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Pixabay did not answer. Try again later.");
        }
    }

    /** The chosen result comes from the browser, so it is checked: only Pixabay addresses are downloaded or linked. */
    private static PixabayHit checked(ChosenResult chosen) {
        if (chosen == null || chosen.kind() == null || chosen.downloadUrl() == null) {
            throw new InvalidAdminRequestException("Send kind, pixabayId, downloadUrl, photographer and pageUrl.");
        }
        if (!chosen.kind().equals("photo") && !chosen.kind().equals("clip")) {
            throw new InvalidAdminRequestException("kind must be photo or clip.");
        }
        if (!isPixabay(chosen.downloadUrl())) {
            throw new InvalidAdminRequestException("downloadUrl must be a Pixabay https address.");
        }
        String pageUrl = chosen.pageUrl() == null ? "" : chosen.pageUrl();
        if (!pageUrl.isEmpty() && !isPixabay(pageUrl)) {
            throw new InvalidAdminRequestException("pageUrl must be a Pixabay https address.");
        }
        String photographer = chosen.photographer() == null || chosen.photographer().isBlank()
                ? "Unknown" : chosen.photographer().trim();
        if (photographer.length() > 255 || pageUrl.length() > 500) {
            throw new InvalidAdminRequestException("photographer or pageUrl is too long.");
        }
        return new PixabayHit(chosen.kind(), chosen.pixabayId(), chosen.downloadUrl(), photographer, pageUrl);
    }

    private static boolean isPixabay(String address) {
        try {
            return HttpPixabayClient.isPixabayAddress(URI.create(address));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
