package com.englishtranslation.backend.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for Sign in with Google and Sign in with Apple (app.auth.social.* in application.properties).
 *
 * @param googleClientIds the OAuth client IDs from Google Cloud (the iOS client, and later a web client). An ID token
 *                        must be made for one of them ("aud"). Empty = Google sign-in is off (503).
 * @param appleClientIds  the bundle IDs (and later a Services ID for the web) Apple makes ID tokens for. Empty = off.
 * @param googleJwksUrl   Google's public signing keys
 * @param appleJwksUrl    Apple's public signing keys
 */
@ConfigurationProperties(prefix = "app.auth.social")
public record SocialAuthProperties(
        List<String> googleClientIds,
        List<String> appleClientIds,
        String googleJwksUrl,
        String appleJwksUrl) {

    /** The allowed "aud" values for this provider, without blanks. */
    public List<String> clientIdsFor(SocialProvider provider) {
        List<String> ids = provider == SocialProvider.GOOGLE ? googleClientIds : appleClientIds;
        return ids == null ? List.of() : ids.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
    }

    public String jwksUrlFor(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? googleJwksUrl : appleJwksUrl;
    }
}
