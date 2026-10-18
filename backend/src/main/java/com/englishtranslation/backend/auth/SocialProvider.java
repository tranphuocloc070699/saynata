package com.englishtranslation.backend.auth;

import java.util.Set;

/**
 * The outside accounts a user can sign in with. Each one gives the app an ID token (a JWT signed by the provider);
 * the backend checks it against the provider's public keys and these issuers.
 */
public enum SocialProvider {
    GOOGLE(Set.of("https://accounts.google.com", "accounts.google.com")),
    APPLE(Set.of("https://appleid.apple.com"));

    private final Set<String> issuers;

    SocialProvider(Set<String> issuers) {
        this.issuers = issuers;
    }

    /** The allowed "iss" values (Google uses two). */
    public Set<String> issuers() {
        return issuers;
    }
}
