package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** The admin blocked this account (TASK-105): no new Claude call. Cached and bundled answers never reach the gate. */
public class AccountBlockedException extends ApiException {

    public AccountBlockedException() {
        super(HttpStatus.FORBIDDEN, "account_blocked",
                "This account can't get new explanations. Words already in Saynata still work.");
    }
}
