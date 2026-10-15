package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Today's Claude budget is used up. Cached answers still work. */
public class DailyLimitReachedException extends ApiException {

    public DailyLimitReachedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "daily_limit_reached",
                "Saynata has reached today's limit for new explanations. Cached words still work. Try again tomorrow.");
    }
}
