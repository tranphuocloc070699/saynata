package com.englishtranslation.backend.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stops password guessing: at most 10 login attempts per IP in 15 minutes.
 * It lives in memory, so a restart clears it. That is fine for one small server.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> attemptsByIp = new ConcurrentHashMap<>();

    /** Counts one attempt. Returns false when this IP has used up its attempts. */
    public boolean tryAcquire(String ip) {
        Instant now = Instant.now();
        Deque<Instant> attempts = attemptsByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(now.minus(WINDOW))) {
                attempts.removeFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }

    /** Used by tests: forget all attempts. */
    public void reset() {
        attemptsByIp.clear();
    }
}
