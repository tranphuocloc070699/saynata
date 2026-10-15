package com.englishtranslation.backend.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for app user accounts (app.auth.* in application.properties).
 *
 * @param userJwtSecret        signs the access tokens (env USER_JWT_SECRET, 32+ characters). Empty = a key is
 *                             derived from ADMIN_JWT_SECRET, or random if that is empty too (see AuthKeys)
 * @param accessTokenLifetime  how long an access token works
 * @param refreshTokenLifetime how long a refresh token works
 * @param codeLifetime         how long an email code works
 * @param maxCodeTries         how many guesses one email code allows
 * @param requireSignIn        true = a guest cannot cause a new Claude call (the rule). Only tests set false.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String userJwtSecret,
        Duration accessTokenLifetime,
        Duration refreshTokenLifetime,
        Duration codeLifetime,
        int maxCodeTries,
        boolean requireSignIn) {
}
