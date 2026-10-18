package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Wrong, used or old sign-in link, or not opened in the app that asked for it. One answer for all. */
public class InvalidLinkException extends ApiException {

    public InvalidLinkException() {
        super(HttpStatus.UNAUTHORIZED, "invalid_link", "This sign-in link is used or has expired. Ask for a new one.");
    }
}
