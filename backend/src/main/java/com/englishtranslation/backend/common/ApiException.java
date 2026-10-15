package com.englishtranslation.backend.common;

import org.springframework.http.HttpStatus;

/**
 * An error the app should understand by its {@code code} (for example "sign_in_required"), not only by the status.
 * ApiExceptionHandler turns it into a problem-details answer with a "code" field; the sentence stream sends
 * the same code in its {@code error} event.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
