package com.englishtranslation.backend.media;

import com.englishtranslation.backend.media.PixabayClient.PixabayHit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * Turns a Pixabay hit (or a file the admin uploaded) into a file on disk and a row in the media table: convert
 * (320 px WebP photo or 3 s MP4 clip), name the file, insert the row. Used by the background job for new backend
 * words and by the admin when it picks another result (TASK-52) or uploads a file (TASK-79). Linking the row to a
 * sense is the caller's job.
 */
@Service
public class MediaStore {

    /** The credit of an uploaded file (media.license_note). The admin shows it as the caption. */
    public static final String UPLOAD_CREDIT = "Uploaded by the admin";
    /** The credit of a picture the admin drew with Codex (TASK-91). The admin shows it as the caption. */
    public static final String AI_CREDIT = "AI image (codex)";

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
        return convertAndInsert(word, position, hit.kind(), original,
                (fileName, bytes) -> insertPixabay(fileName, hit, bytes));
    }

    /**
     * Saves a file the admin uploaded as the picture of sense number {@code position} of {@code word}.
     * {@code kind} is "photo" or "clip" and comes from the magic bytes of the file (UploadedFileType). The conversion
     * and the file name are the same as for Pixabay media. The row has source "upload" and no Pixabay credit.
     * Returns the id of the media row.
     */
    public long saveUpload(String word, int position, String kind, byte[] original) throws IOException {
        return convertAndInsert(word, position, kind, original, (fileName, bytes) -> insertUpload(fileName, kind, bytes));
    }

    /**
     * Saves a PNG that Codex drew (TASK-91) as the picture of sense number {@code position} of {@code word}: cropped to
     * 3:2, at most 1536 px wide, a WebP named {@code <word>_<n>_ai_<8 hex of sha256>.webp} (like the pipeline's
     * media-ai). The row has source "ai" and the credit "AI image (codex)". Returns the id of the media row.
     */
    public long saveAi(String word, int position, byte[] png) throws IOException {
        int[] size = aiSize(png);
        return convertAndInsert(word, position, "photo", "ai_", target -> converter.toAiPicture(png, target),
                (fileName, bytes) -> insertAi(fileName, bytes, size));
    }

    /**
     * The size of the saved AI picture, worked out from the PNG with the same rule as the ffmpeg filter (crop to 3:2
     * around the middle, at most 1536 px wide, even height). Null when the PNG cannot be read.
     */
    static int[] aiSize(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                return null;
            }
            int cropWidth = (int) Math.round(Math.min(image.getWidth(), image.getHeight() * 1.5));
            int cropHeight = (int) Math.round(Math.min(image.getHeight(), image.getWidth() / 1.5));
            int width = Math.min(FfmpegMediaConverter.AI_MAX_WIDTH, cropWidth);
            int height = (int) Math.round(width * (double) cropHeight / cropWidth / 2.0) * 2;
            return new int[] {width, height};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Inserts the media row of a file that is already on disk and returns its id. */
    private interface RowInserter {
        long insert(String fileName, int bytes);
    }

    /** Converts the original into the file {@code target}. */
    private interface Conversion {
        void convert(Path target);
    }

    private long convertAndInsert(String word, int position, String kind, byte[] original, RowInserter inserter)
            throws IOException {
        return convertAndInsert(word, position, kind, "", target -> {
            if (kind.equals("clip")) {
                converter.toClip(original, target);
            } else {
                converter.toPhoto(original, target);
            }
        }, inserter);
    }

    /** {@code nameTag} is "" or "ai_": it goes between the sense number and the hash in the file name. */
    private long convertAndInsert(String word, int position, String kind, String nameTag, Conversion conversion,
            RowInserter inserter) throws IOException {
        Path dir = Path.of(properties.dir());
        Files.createDirectories(dir);
        String extension = kind.equals("clip") ? "mp4" : "webp";
        Path temp = dir.resolve(".tmp-" + UUID.randomUUID() + "." + extension);
        try {
            conversion.convert(temp);
            byte[] saved = Files.readAllBytes(temp);
            // The hash makes the name unique per content, so Caddy can cache the file for a year.
            String fileName = fileSafe(word) + "_" + (position + 1) + "_" + nameTag + shortHash(saved) + "." + extension;
            Files.move(temp, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            // Same content, same name: this file was used before (for example the admin picked the same result again).
            Optional<Long> existing = jdbc.sql("select id from media where file_name = :file").param("file", fileName)
                    .query(Long.class).optional();
            if (existing.isPresent()) {
                return existing.get();
            }
            try {
                return inserter.insert(fileName, saved.length);
            } catch (RuntimeException e) {
                Files.deleteIfExists(dir.resolve(fileName));
                throw e;
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private long insertPixabay(String fileName, PixabayHit hit, int bytes) {
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

    private long insertUpload(String fileName, String kind, int bytes) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("insert into media (file_name, kind, bytes, license_note, source, created_at)"
                + " values (:file, :kind, :bytes, :credit, 'upload', :now)")
                .param("file", fileName).param("kind", kind).param("bytes", bytes)
                .param("credit", UPLOAD_CREDIT).param("now", OffsetDateTime.now())
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    private long insertAi(String fileName, int bytes, int[] size) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("insert into media (file_name, kind, width, height, bytes, license_note, source, created_at)"
                + " values (:file, 'photo', :width, :height, :bytes, :credit, 'ai', :now)")
                .param("file", fileName).param("width", size == null ? null : size[0])
                .param("height", size == null ? null : size[1]).param("bytes", bytes)
                .param("credit", AI_CREDIT).param("now", OffsetDateTime.now())
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
