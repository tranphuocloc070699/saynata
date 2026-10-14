package com.englishtranslation.backend.media;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to https://pixabay.com/api/ . Same searches as pipeline/src/pipeline/media.py.
 * Pixabay allows 100 requests per 60 seconds. We wait at least 1.5 seconds between requests (40 per minute),
 * and the media executor has one thread, so we stay well below the limit.
 */
@Component
public class HttpPixabayClient implements PixabayClient {

    private static final String PHOTO_URL = "https://pixabay.com/api/";
    private static final String VIDEO_URL = "https://pixabay.com/api/videos/";
    private static final long MIN_GAP_MILLIS = 1500;
    private static final int MAX_BYTES = 30 * 1024 * 1024;

    private final MediaProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private long lastCallAt = 0;

    public HttpPixabayClient(MediaProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<PixabayHit> findPhoto(String query) {
        JsonNode top = firstHit(PHOTO_URL, query);
        if (top == null) {
            return Optional.empty();
        }
        String url = text(top, "webformatURL");
        if (url.isEmpty()) {
            url = text(top, "largeImageURL");
        }
        return url.isEmpty() ? Optional.empty() : Optional.of(hit("photo", top, url));
    }

    @Override
    public Optional<PixabayHit> findClip(String query) {
        JsonNode top = firstHit(VIDEO_URL, query);
        if (top == null) {
            return Optional.empty();
        }
        // Small sizes first: quick to download and to convert.
        for (String size : new String[] {"tiny", "small", "medium"}) {
            String url = text(top.path("videos").path(size), "url");
            if (!url.isEmpty()) {
                return Optional.of(hit("clip", top, url));
            }
        }
        return Optional.empty();
    }

    @Override
    public byte[] download(String url) {
        URI uri = URI.create(url);
        // Only download from Pixabay, whatever the answer said.
        if (!isPixabayAddress(uri)) {
            throw new IllegalArgumentException("Not a Pixabay https address: " + uri.getHost());
        }
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Pixabay download answered " + response.statusCode());
        }
        if (response.body().length > MAX_BYTES) {
            throw new IllegalStateException("Pixabay file is too big: " + response.body().length + " bytes");
        }
        return response.body();
    }

    private JsonNode firstHit(String baseUrl, String query) {
        String url = baseUrl + "?key=" + encode(properties.pixabayApiKey()) + "&q=" + encode(query)
                + "&safesearch=true&order=popular&per_page=3" + (baseUrl.equals(PHOTO_URL) ? "&image_type=photo" : "");
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // The URL has the API key in it, so it is not part of the message.
            throw new IllegalStateException("Pixabay search answered " + response.statusCode());
        }
        JsonNode hits = jsonMapper.readTree(response.body()).path("hits");
        return hits.isArray() && !hits.isEmpty() ? hits.get(0) : null;
    }

    private PixabayHit hit(String kind, JsonNode top, String downloadUrl) {
        String photographer = text(top, "user");
        return new PixabayHit(kind, top.path("id").asLong(), downloadUrl,
                photographer.isEmpty() ? "Unknown" : photographer, text(top, "pageURL"));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        waitForTurn();
        try {
            return http.send(request, handler);
        } catch (IOException e) {
            throw new IllegalStateException("Pixabay request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Pixabay", e);
        }
    }

    /** Waits until at least MIN_GAP_MILLIS have passed since the last request. */
    private synchronized void waitForTurn() {
        long wait = MIN_GAP_MILLIS - (System.currentTimeMillis() - lastCallAt);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    /** True only for https on pixabay.com or one of its subdomains (cdn.pixabay.com), not "evilpixabay.com". */
    static boolean isPixabayAddress(URI uri) {
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
                && (host.equals("pixabay.com") || host.endsWith(".pixabay.com"));
    }
}
