package com.englishtranslation.backend.media;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to https://pixabay.com/api/ . Same searches as pipeline/src/pipeline/media.py.
 * Pixabay allows 100 requests per 60 seconds. We wait at least 1.5 seconds between requests (40 per minute),
 * and the media executor has one thread, so we stay well below the limit.
 * The key is shared (admin search, background jobs, a media pass), so Pixabay can still answer 429. Then we try again:
 * 3 tries in all, with a pause between them, and about 10 seconds of waiting at most (TASK-63).
 */
@Component
public class HttpPixabayClient implements PixabayClient {

    private static final String PHOTO_URL = "https://pixabay.com/api/";
    private static final String VIDEO_URL = "https://pixabay.com/api/videos/";
    private static final long MIN_GAP_MILLIS = 1500;
    private static final int MAX_BYTES = 30 * 1024 * 1024;

    private static final int MAX_TRIES = 3;
    private static final long FIRST_PAUSE_MILLIS = 1000;
    private static final long MAX_TOTAL_PAUSE_MILLIS = 10_000;

    private final MediaProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient http;
    private final LongConsumer sleeper;
    private final long minGapMillis;

    private long lastCallAt = 0;

    @Autowired
    public HttpPixabayClient(MediaProperties properties, JsonMapper jsonMapper) {
        this(properties, jsonMapper,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build(),
                HttpPixabayClient::sleep, MIN_GAP_MILLIS);
    }

    /** For tests: a fake HTTP client and a sleeper that does not really wait. */
    HttpPixabayClient(MediaProperties properties, JsonMapper jsonMapper, HttpClient http, LongConsumer sleeper,
            long minGapMillis) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.http = http;
        this.sleeper = sleeper;
        this.minGapMillis = minGapMillis;
    }

    @Override
    public Optional<PixabayHit> findPhoto(String query) {
        List<JsonNode> found = hits(PHOTO_URL, query, 3);
        return found.isEmpty() ? Optional.empty() : photoHit(found.get(0));
    }

    @Override
    public Optional<PixabayHit> findClip(String query) {
        List<JsonNode> found = hits(VIDEO_URL, query, 3);
        return found.isEmpty() ? Optional.empty() : clipHit(found.get(0));
    }

    /** Photos first, then up to 2 clips. If one kind has less, the other fills the free places. */
    @Override
    public List<PixabayHit> search(String query, int limit) {
        List<PixabayHit> photos = new ArrayList<>();
        for (JsonNode node : hits(PHOTO_URL, query, Math.max(limit, 3))) {
            photoHit(node).ifPresent(photos::add);
        }
        List<PixabayHit> clips = new ArrayList<>();
        for (JsonNode node : hits(VIDEO_URL, query, 3)) {
            clipHit(node).ifPresent(clips::add);
        }
        int clipCount = Math.min(clips.size(), Math.min(2, limit));
        int photoCount = Math.min(photos.size(), limit - clipCount);
        // Not enough photos: more clips take the free places.
        clipCount = Math.min(clips.size(), limit - photoCount);
        List<PixabayHit> result = new ArrayList<>(photos.subList(0, photoCount));
        result.addAll(clips.subList(0, clipCount));
        return result;
    }

    private Optional<PixabayHit> photoHit(JsonNode node) {
        String url = text(node, "webformatURL");
        if (url.isEmpty()) {
            url = text(node, "largeImageURL");
        }
        String preview = text(node, "previewURL");
        return url.isEmpty() ? Optional.empty() : Optional.of(hit("photo", node, url, preview.isEmpty() ? url : preview));
    }

    private Optional<PixabayHit> clipHit(JsonNode node) {
        // Small sizes first: quick to download and to convert.
        for (String size : new String[] {"tiny", "small", "medium"}) {
            String url = text(node.path("videos").path(size), "url");
            if (!url.isEmpty()) {
                return Optional.of(hit("clip", node, url, clipPreview(node)));
            }
        }
        return Optional.empty();
    }

    /** The video thumbnail: Pixabay gives one per size, or we build it from picture_id (Pixabay's video host). */
    private static String clipPreview(JsonNode node) {
        for (String size : new String[] {"medium", "small", "tiny"}) {
            String thumbnail = text(node.path("videos").path(size), "thumbnail");
            if (!thumbnail.isEmpty()) {
                return thumbnail;
            }
        }
        String pictureId = text(node, "picture_id");
        return pictureId.isEmpty() ? "" : "https://i.vimeocdn.com/video/" + pictureId + "_295x166.jpg";
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

    private List<JsonNode> hits(String baseUrl, String query, int perPage) {
        String url = baseUrl + "?key=" + encode(properties.pixabayApiKey()) + "&q=" + encode(query)
                + "&safesearch=true&order=popular&per_page=" + perPage
                + (baseUrl.equals(PHOTO_URL) ? "&image_type=photo" : "");
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // The URL has the API key in it, so it is not part of the message.
            throw new IllegalStateException("Pixabay search answered " + response.statusCode());
        }
        List<JsonNode> result = new ArrayList<>();
        JsonNode hits = jsonMapper.readTree(response.body()).path("hits");
        if (hits.isArray()) {
            hits.forEach(result::add);
        }
        return result;
    }

    private PixabayHit hit(String kind, JsonNode top, String downloadUrl, String previewUrl) {
        String photographer = text(top, "user");
        return new PixabayHit(kind, top.path("id").asLong(), downloadUrl,
                photographer.isEmpty() ? "Unknown" : photographer, text(top, "pageURL"), previewUrl);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Sends the request. On 429 it waits and tries again (see the class comment); after the last try it throws PixabayBusyException. */
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        long waited = 0;
        for (int attempt = 1; ; attempt++) {
            HttpResponse<T> response = sendOnce(request, handler);
            if (response.statusCode() != 429) {
                return response;
            }
            long pause = pauseBeforeRetry(response, attempt);
            if (attempt >= MAX_TRIES || waited + pause > MAX_TOTAL_PAUSE_MILLIS) {
                throw new PixabayBusyException("Pixabay answered 429 (too many requests)");
            }
            waited += pause;
            sleeper.accept(pause);
        }
    }

    /** The Retry-After header (seconds) when Pixabay sends one, else 1 s, 2 s, 4 s... */
    private static long pauseBeforeRetry(HttpResponse<?> response, int attempt) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            try {
                return Math.max(0, Long.parseLong(header.get().trim())) * 1000;
            } catch (NumberFormatException e) {
                // A date or something else: use our own pause.
            }
        }
        return FIRST_PAUSE_MILLIS << (attempt - 1);
    }

    private <T> HttpResponse<T> sendOnce(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
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

    /** Waits until at least minGapMillis have passed since the last request. */
    private synchronized void waitForTurn() {
        long wait = minGapMillis - (System.currentTimeMillis() - lastCallAt);
        if (wait > 0) {
            sleeper.accept(wait);
        }
        lastCallAt = System.currentTimeMillis();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** True only for https on pixabay.com or one of its subdomains (cdn.pixabay.com), not "evilpixabay.com". */
    public static boolean isPixabayAddress(URI uri) {
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && host != null
                && (host.equals("pixabay.com") || host.endsWith(".pixabay.com"));
    }
}
