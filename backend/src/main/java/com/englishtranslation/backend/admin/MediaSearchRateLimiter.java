package com.englishtranslation.backend.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Protects our Pixabay key: one admin may search at most 30 times in 5 minutes. Each search is two Pixabay calls
 * and Pixabay allows 100 calls per minute for the whole key, so this keeps us far below it.
 * It lives in memory, so a restart clears it (same idea as LoginRateLimiter).
 */
@Component
public class MediaSearchRateLimiter {

    static final int MAX_SEARCHES = 30;
    static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> searchesByAdmin = new ConcurrentHashMap<>();

    /** Counts one search. Returns false when this admin has used up the searches for now. */
    public boolean tryAcquire(String admin) {
        Instant now = Instant.now();
        Deque<Instant> searches = searchesByAdmin.computeIfAbsent(admin, key -> new ArrayDeque<>());
        synchronized (searches) {
            while (!searches.isEmpty() && searches.peekFirst().isBefore(now.minus(WINDOW))) {
                searches.removeFirst();
            }
            if (searches.size() >= MAX_SEARCHES) {
                return false;
            }
            searches.addLast(now);
            return true;
        }
    }

    /** Used by tests: forget all searches. */
    public void reset() {
        searchesByAdmin.clear();
    }
}
