package com.saynata.backend.common;

import org.springframework.http.HttpStatus;

/**
 * An error the client can understand by its {@code code} (for example "word_not_found"), not only by the status.
 * The status is for machines in the middle (proxies, browsers); the code is for our own apps (web, iOS).
 * ApiExceptionHandler turns it into a Problem Details answer with a "code" field.
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
