package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Wrong code, old code, too many tries, or no code at all. One answer for all, so nothing is revealed. */
public class InvalidCodeException extends ApiException {

    public InvalidCodeException() {
        super(HttpStatus.UNAUTHORIZED, "invalid_code", "That code is wrong or has expired. Ask for a new code.");
    }
}
