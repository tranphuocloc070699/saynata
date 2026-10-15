package com.englishtranslation.backend.media;

import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * Turns a Pixabay hit into a file on disk and a row in the media table: download, convert (320 px WebP photo or
 * 3 s MP4 clip), name the file, insert the row. Used by the background job for new backend words and by the admin
 * when it picks another result (TASK-52). Linking the row to a sense is the caller's job.
 */
@Service
public class MediaStore {

    private final PixabayClient pixabay;
    private final MediaConverter converter;
    private final MediaProperties properties;
    private final JdbcClient jdbc;

    public MediaStore(PixabayClient pixabay, MediaConverter converter, MediaProperties properties, JdbcClient jdbc) {
        this.pixabay = pixabay;
        this.converter = converter;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    /**
     * Saves the hit as the picture of sense number {@code position} (0 = first) of {@code word}.
     * Returns the id of the new media row. Throws if the download, the conversion or the database fails; then no
     * file is left behind.
     */
    public long save(String word, int position, PixabayHit hit) throws IOException {
        byte[] original = pixabay.download(hit.downloadUrl());

        Path dir = Path.of(properties.dir());
        Files.createDirectories(dir);
        String extension = hit.kind().equals("clip") ? "mp4" : "webp";
        Path temp = dir.resolve(".tmp-" + UUID.randomUUID() + "." + extension);
        try {
            if (hit.kind().equals("clip")) {
                converter.toClip(original, temp);
            } else {
                converter.toPhoto(original, temp);
            }
            byte[] saved = Files.readAllBytes(temp);
            // The hash makes the name unique per content, so Caddy can cache the file for a year.
            String fileName = fileSafe(word) + "_" + (position + 1) + "_" + shortHash(saved) + "." + extension;
            Files.move(temp, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            // Same content, same name: the admin picked a result that was used for this sense before.
            Optional<Long> existing = jdbc.sql("select id from media where file_name = :file").param("file", fileName)
                    .query(Long.class).optional();
            if (existing.isPresent()) {
                return existing.get();
            }
            try {
                return insertMedia(fileName, hit, saved.length);
            } catch (RuntimeException e) {
                Files.deleteIfExists(dir.resolve(fileName));
                throw e;
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private long insertMedia(String fileName, PixabayHit hit, int bytes) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("insert into media (file_name, kind, bytes, pixabay_id, photographer, page_url, license_note, created_at)"
                + " values (:file, :kind, :bytes, :pixabayId, :photographer, :pageUrl, :license, :now)")
                .param("file", fileName).param("kind", hit.kind()).param("bytes", bytes)
                .param("pixabayId", hit.id()).param("photographer", hit.photographer())
                .param("pageUrl", hit.pageUrl()).param("license", "Pixabay Content License")
                .param("now", OffsetDateTime.now())
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    /** "don't" -> "don_t": letters and digits only, so the name is safe in a URL and on disk. */
    static String fileSafe(String word) {
        return word.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    private static String shortHash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
