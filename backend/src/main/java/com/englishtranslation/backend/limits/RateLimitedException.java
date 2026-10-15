package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Too many requests. {@code retryAfterSeconds} goes into the Retry-After header. */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many requests. Try again in a little while.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
