package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** The refresh token is unknown, expired, or was already used. The app must sign in again. */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Your session has ended. Sign in again.");
    }
}
