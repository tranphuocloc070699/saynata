package com.englishtranslation.backend.cache;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A cachedOnly request (TASK-83.4) found no saved or bundled answer. The app asks this while the user types,
 * so it must never cause a Claude call: this is thrown before ClaudeGate, for guests and users alike.
 */
public class NotCachedException extends ApiException {

    public NotCachedException() {
        super(HttpStatus.NOT_FOUND, "not_cached", "No saved answer yet.");
    }
}
