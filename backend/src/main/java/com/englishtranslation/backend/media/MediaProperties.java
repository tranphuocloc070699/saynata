package com.englishtranslation.backend.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param dir            folder for the media files (env MEDIA_DIR). On the VPS it is a Docker volume that Caddy also reads.
 * @param baseUrl        public address of that folder (env MEDIA_BASE_URL)
 * @param pixabayApiKey  env PIXABAY_API_KEY. Empty = the background job does nothing.
 */
@ConfigurationProperties("app.media")
public record MediaProperties(String dir, String baseUrl, String pixabayApiKey) {

    public boolean pixabayEnabled() {
        return pixabayApiKey != null && !pixabayApiKey.isBlank();
    }

    /** The public address of one file: baseUrl + "/" + file name. */
    public String urlFor(String fileName) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + fileName;
    }
}
