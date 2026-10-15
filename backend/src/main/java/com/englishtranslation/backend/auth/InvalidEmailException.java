package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidEmailException extends ApiException {

    public InvalidEmailException() {
        super(HttpStatus.BAD_REQUEST, "invalid_email", "Enter a valid email address.");
    }
}
