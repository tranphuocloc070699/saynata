package com.englishtranslation.backend.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishtranslation.backend.limits.RateLimiter.Limit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The token bucket, with a clock the test moves by hand. */
class RateLimiterTest {

    /** A clock that only moves when the test says so. */
    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final RateLimiter limiter = new RateLimiter(clock);
    private final Limit threePerFifteenMinutes = new Limit("three", 3, Duration.ofMinutes(15));

    @Test
    void allowsTheMaximumThenRefusesWithARetryTime() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a@b.c", threePerFifteenMinutes).allowed()).isTrue();
        }

        RateLimiter.Decision fourth = limiter.tryAcquire("a@b.c", threePerFifteenMinutes);

        assertThat(fourth.allowed()).isFalse();
        // One token comes back every 5 minutes.
        assertThat(fourth.retryAfterSeconds()).isEqualTo(300);
    }

    @Test
    void tokensComeBackOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a@b.c", threePerFifteenMinutes);
        }
        clock.advance(Duration.ofMinutes(5));

        assertThat(limiter.tryAcquire("a@b.c", threePerFifteenMinutes).allowed()).isTrue();
        assertThat(limiter.tryAcquire("a@b.c", threePerFifteenMinutes).allowed()).isFalse();
    }

    @Test
    void eachKeyHasItsOwnBucket() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("one", threePerFifteenMinutes);
        }

        assertThat(limiter.tryAcquire("one", threePerFifteenMinutes).allowed()).isFalse();
        assertThat(limiter.tryAcquire("two", threePerFifteenMinutes).allowed()).isTrue();
    }

    @Test
    void whenOneLimitIsEmptyTheOtherIsNotUsedUp() {
        Limit perHour = new Limit("hour", 60, Duration.ofHours(1));
        Limit perDay = new Limit("day", 1, Duration.ofDays(1));
        assertThat(limiter.tryAcquire("u", perHour, perDay).allowed()).isTrue();

        // The day limit is empty now. Ten refused tries must not eat the hour tokens.
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("u", perHour, perDay).allowed()).isFalse();
        }
        assertThat(limiter.tryAcquire("u", perHour).allowed()).isTrue();
        // 60 - 1 (first call) - 1 (this call) = 58 left.
        int more = 0;
        while (limiter.tryAcquire("u", perHour).allowed()) {
            more++;
        }
        assertThat(more).isEqualTo(58);
    }

    @Test
    void acquireOrThrowGives429WithTheWait() {
        for (int i = 0; i < 3; i++) {
            limiter.acquireOrThrow("k", threePerFifteenMinutes);
        }

        assertThatThrownBy(() -> limiter.acquireOrThrow("k", threePerFifteenMinutes))
                .isInstanceOfSatisfying(RateLimitedException.class, e -> {
                    assertThat(e.status().value()).isEqualTo(429);
                    assertThat(e.code()).isEqualTo("rate_limited");
                    assertThat(e.retryAfterSeconds()).isEqualTo(300);
                });
    }
}
