package com.englishtranslation.backend.admin;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for /api/admin (app.admin.* in application.properties).
 *
 * @param enabled         false turns the whole admin API off (then no secret is needed)
 * @param jwtSecret       signs the login cookie; at least 32 characters (env ADMIN_JWT_SECRET)
 * @param email           email of the first admin (env ADMIN_EMAIL)
 * @param initialPassword password of the first admin (env ADMIN_INITIAL_PASSWORD)
 * @param cookieSecure    the cookie is only sent over HTTPS; keep true except for odd local setups
 * @param allowedOrigins  web pages that may call /api/admin from a browser (CORS)
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        boolean enabled,
        String jwtSecret,
        String email,
        String initialPassword,
        boolean cookieSecure,
        List<String> allowedOrigins) {
}
