package com.englishtranslation.backend.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the emailed sign-in link (app.auth.link.* in application.properties). No domain is written in the
 * code: when the domain moves, only these values (and the app's Info.plist) change. See doc-17.
 *
 * @param lifetime      how long a link works
 * @param publicBaseUrl the public HTTPS address of this backend, for example https://api.saynata.com. The email links
 *                      to {publicBaseUrl}/auth/link?token=...
 * @param appUrlScheme  the app's own URL scheme. The link page opens {scheme}://sign-in?token=... (free Apple ID).
 * @param appleAppIds   "TEAMID.bundle.id" values for the apple-app-site-association file. Empty = no file (404).
 *                      With the paid account and Associated Domains, iOS then opens the link in the app directly.
 */
@ConfigurationProperties(prefix = "app.auth.link")
public record SignInLinkProperties(
        Duration lifetime,
        String publicBaseUrl,
        String appUrlScheme,
        List<String> appleAppIds) {

    /** The address put in the email. */
    public String emailUrl(String token) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/auth/link?token=" + token;
    }

    /** The address that opens the app with its custom URL scheme. */
    public String appUrl(String token) {
        return appUrlScheme + "://sign-in?token=" + token;
    }

    public List<String> appleAppIdList() {
        return appleAppIds == null ? List.of()
                : appleAppIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
    }
}
