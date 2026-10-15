package com.englishtranslation.backend.media;

import java.util.List;
import java.util.Optional;

/** The things we need from Pixabay. The real one is HttpPixabayClient; tests use a fake. */
public interface PixabayClient {

    /** The best photo for the search words, or empty when Pixabay has none. Throws on network errors. */
    Optional<PixabayHit> findPhoto(String query);

    /** The best short video for the search words, or empty. Throws on network errors. */
    Optional<PixabayHit> findClip(String query);

    /**
     * Several results for the admin to choose from (TASK-52): mostly photos, up to 2 clips, at most {@code limit}.
     * Every hit has a previewUrl. Throws on network errors.
     */
    List<PixabayHit> search(String query, int limit);

    /** Downloads the file of a hit. */
    byte[] download(String url);

    /**
     * @param kind         "photo" or "clip"
     * @param id           Pixabay id
     * @param downloadUrl  where to download the file
     * @param photographer Pixabay user name
     * @param pageUrl      the Pixabay page of the file
     * @param previewUrl   a small picture to show in the admin (only search fills it)
     */
    record PixabayHit(String kind, long id, String downloadUrl, String photographer, String pageUrl,
            String previewUrl) {

        public PixabayHit(String kind, long id, String downloadUrl, String photographer, String pageUrl) {
            this(kind, id, downloadUrl, photographer, pageUrl, "");
        }
    }
}
