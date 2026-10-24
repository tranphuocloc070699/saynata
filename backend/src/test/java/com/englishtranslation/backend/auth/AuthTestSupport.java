package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.user.User;

/** For tests in other packages: the end of a real sign-in (tokens, last seen) without an email or an ID token. */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static AuthService.TokenResponse signIn(AuthService auth, User user) {
        return auth.issueTokensFor(user);
    }
}
