package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** A guest asked for something that needs a new Claude call. */
public class SignInRequiredException extends ApiException {

    public SignInRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "sign_in_required", "Sign in to get a new explanation.");
    }
}
