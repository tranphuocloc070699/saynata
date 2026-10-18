package com.englishtranslation.backend.auth;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The web side of the emailed sign-in link (doc-17).
 * <ul>
 * <li>{@code GET /auth/link?token=...}: the address in the email. A small page that opens the app with its URL
 * scheme ({scheme}://sign-in?token=...). It does not use the token up: only the app can, with its secret.
 * Mail clients do not open custom schemes from an email, so the email needs this HTTPS page in between.</li>
 * <li>{@code GET /.well-known/apple-app-site-association}: for later, with the paid Apple account. When
 * APPLE_APP_IDS is set and the app has the Associated Domains entitlement, iOS opens /auth/link in the app
 * directly (a universal link) and this page is not shown at all.</li>
 * </ul>
 */
@RestController
public class SignInLinkPageController {

    /** 32 random bytes in URL-safe base64 are 43 characters; allow some room, but nothing that needs escaping. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,100}");

    private final SignInLinkProperties properties;

    public SignInLinkPageController(SignInLinkProperties properties) {
        this.properties = properties;
    }

    static boolean looksLikeToken(String token) {
        return token != null && TOKEN.matcher(token.trim()).matches();
    }

    @GetMapping(value = "/auth/link", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> linkPage(@RequestParam(name = "token", required = false) String token) {
        String body = looksLikeToken(token) ? openAppPage(properties.appUrl(token.trim())) : brokenLinkPage();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                // The token is in this page's address: never send it to another site.
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'")
                .body(body);
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        List<String> appIds = properties.appleAppIdList();
        if (appIds.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("applinks", Map.of("details", List.of(Map.of(
                "appIDs", appIds,
                "components", List.of(Map.of("/", "/auth/link*")))))));
    }

    private static String openAppPage(String appUrl) {
        String href = HtmlUtils.htmlEscape(appUrl);
        return page("""
                <h1>Open Saynata</h1>
                <p>Tap the button to finish signing in.</p>
                <p><a id="open" class="button" href="%s">Open Saynata</a></p>
                <p class="small">Open this link on the iPhone where you asked for it. The link works once.</p>
                <script>window.location.href = document.getElementById("open").getAttribute("href");</script>
                """.formatted(href));
    }

    private static String brokenLinkPage() {
        return page("""
                <h1>This link does not work</h1>
                <p>Open Saynata and ask for a new sign-in link.</p>
                """);
    }

    private static String page(String content) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>Saynata sign-in</title>
                <style>
                body { font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 48px 24px;
                       color: #1f2933; background: #faf9f6; text-align: center; }
                h1 { font-size: 24px; }
                .button { display: inline-block; padding: 14px 24px; border-radius: 12px; background: #0f766e;
                          color: #fff; text-decoration: none; font-weight: 600; }
                .small { color: #5f6b76; font-size: 14px; }
                </style>
                </head>
                <body>
                %s</body>
                </html>
                """.formatted(content);
    }
}
