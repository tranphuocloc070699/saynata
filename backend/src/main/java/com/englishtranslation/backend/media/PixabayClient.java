package com.englishtranslation.backend.media;

import java.util.Optional;

/** The three things we need from Pixabay. The real one is HttpPixabayClient; tests use a fake. */
public interface PixabayClient {

    /** The best photo for the search words, or empty when Pixabay has none. Throws on network errors. */
    Optional<PixabayHit> findPhoto(String query);

    /** The best short video for the search words, or empty. Throws on network errors. */
    Optional<PixabayHit> findClip(String query);

    /** Downloads the file of a hit. */
    byte[] download(String url);

    /**
     * @param kind         "photo" or "clip"
     * @param id           Pixabay id
     * @param downloadUrl  where to download the file
     * @param photographer Pixabay user name
     * @param pageUrl      the Pixabay page of the file
     */
    record PixabayHit(String kind, long id, String downloadUrl, String photographer, String pageUrl) {
    }
}
