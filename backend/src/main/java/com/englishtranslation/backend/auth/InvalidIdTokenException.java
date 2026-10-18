package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** The Google or Apple ID token is not good: wrong signature, wrong app, too old, or the nonce does not match. */
public class InvalidIdTokenException extends ApiException {

    public InvalidIdTokenException() {
        this("Sign-in did not work. Try again.");
    }

    public InvalidIdTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "invalid_id_token", message);
    }
}
