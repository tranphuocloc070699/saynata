package com.englishtranslation.backend.limits;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A small in-memory token bucket. A rule "5 per hour" is a bucket of 5 tokens that refills evenly over the
 * hour. Each request takes one token; with no token left the request is refused and we say how long to wait.
 * It lives in memory, so a restart clears it. That is fine for one server (no Redis).
 */
@Component
public class RateLimiter {

    /** One rule: at most {@code max} requests per {@code period}. The name keeps different rules apart. */
    public record Limit(String name, int max, Duration period) {
    }

    /** {@code retryAfterSeconds} is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final int PRUNE_ABOVE = 10_000;
    private static final Duration PRUNE_EVERY = Duration.ofMinutes(1);
    /** A bucket idle this long is full again for every rule we use, so it can be forgotten. */
    private static final Duration IDLE_FORGET = Duration.ofDays(1);

    private static final class Bucket {
        double tokens;
        Instant updated;

        Bucket(double tokens, Instant updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }

    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private Instant lastPrune;

    public RateLimiter(Clock clock) {
        this.clock = clock;
        this.lastPrune = clock.instant();
    }

    /**
     * Takes one token from every limit for this key, or from none: when one limit is empty, nothing is used up.
     * (Synchronized: the check and the take must happen together.)
     */
    public synchronized Decision tryAcquire(String key, Limit... limits) {
        Instant now = clock.instant();
        prune(now);
        long wait = 0;
        for (Limit limit : limits) {
            Bucket bucket = refilled(limit, key, now);
            if (bucket.tokens < 1) {
                double perSecond = limit.max() / (double) limit.period().toSeconds();
                wait = Math.max(wait, (long) Math.ceil((1 - bucket.tokens) / perSecond));
            }
        }
        if (wait > 0) {
            return new Decision(false, wait);
        }
        for (Limit limit : limits) {
            buckets.get(limit.name() + ":" + key).tokens -= 1;
        }
        return new Decision(true, 0);
    }

    /** Throws {@link RateLimitedException} (HTTP 429) when a limit is used up. */
    public void acquireOrThrow(String key, Limit... limits) {
        Decision decision = tryAcquire(key, limits);
        if (!decision.allowed()) {
            throw new RateLimitedException(decision.retryAfterSeconds());
        }
    }

    private Bucket refilled(Limit limit, String key, Instant now) {
        Bucket bucket = buckets.computeIfAbsent(limit.name() + ":" + key, k -> new Bucket(limit.max(), now));
        double elapsedSeconds = Duration.between(bucket.updated, now).toMillis() / 1000.0;
        double perSecond = limit.max() / (double) limit.period().toSeconds();
        bucket.tokens = Math.min(limit.max(), bucket.tokens + elapsedSeconds * perSecond);
        bucket.updated = now;
        return bucket;
    }

    /** Forgets idle buckets when there are many. Runs at most once a minute. */
    private void prune(Instant now) {
        if (buckets.size() < PRUNE_ABOVE || Duration.between(lastPrune, now).compareTo(PRUNE_EVERY) < 0) {
            return;
        }
        lastPrune = now;
        buckets.values().removeIf(bucket -> Duration.between(bucket.updated, now).compareTo(IDLE_FORGET) > 0);
    }

    /** Used by tests: forget everything. */
    public synchronized void reset() {
        buckets.clear();
    }
}
