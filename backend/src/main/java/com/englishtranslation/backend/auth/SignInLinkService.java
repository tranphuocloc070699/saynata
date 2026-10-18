package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.LimitProperties;
import com.englishtranslation.backend.limits.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sign in with a link in an email ("magic link").
 * <ol>
 * <li>{@link #requestLink}: the app sends the email and the SHA-256 of a secret it keeps (the code challenge).
 * We make a random token, save only its hash, and email {publicBaseUrl}/auth/link?token=...</li>
 * <li>The link page ({@link SignInLinkPageController}) opens the app. Opening the page uses nothing up, so a mail
 * scanner that visits every link cannot spend it.</li>
 * <li>{@link #verifyLink}: the app sends the token and its secret. Both must match; the link then works no more.</li>
 * </ol>
 * The secret is the PKCE idea (RFC 7636): another app that also registered our URL scheme could catch the token,
 * but without the secret, which never leaves our app, the token is useless.
 */
@Service
public class SignInLinkService {

    private static final Logger log = LoggerFactory.getLogger(SignInLinkService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailLoginLinkRepository links;
    private final AuthService auth;
    private final EmailSender emailSender;
    private final SignInLinkProperties properties;
    private final RateLimiter rateLimiter;
    private final LimitProperties limits;
    private final Clock clock;

    public SignInLinkService(EmailLoginLinkRepository links, AuthService auth, EmailSender emailSender,
            SignInLinkProperties properties, RateLimiter rateLimiter, LimitProperties limits, Clock clock) {
        this.links = links;
        this.auth = auth;
        this.emailSender = emailSender;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
        this.clock = clock;
    }

    /** Always quiet, like the email code: the controller answers 204 whether or not the email has an account. */
    public void requestLink(String rawEmail, String codeChallenge, String clientIp) {
        String email = Emails.normalize(rawEmail);
        if (codeChallenge == null || !codeChallenge.trim().matches("[0-9a-f]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_code_challenge",
                    "codeChallenge must be the SHA-256 of the app's secret, as 64 hex characters.");
        }
        // The same limits as the email code: together they cap the emails one address or one IP can cause.
        rateLimiter.acquireOrThrow(clientIp, limits.emailCodePerIpLimit());
        rateLimiter.acquireOrThrow(email, limits.emailCodePerEmailLimit());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        links.deleteAllFor(email); // one link per email at a time
        links.save(new EmailLoginLink(AuthService.sha256(token), email, codeChallenge.trim(), now,
                now.plus(properties.lifetime())));
        try {
            emailSender.sendSignInLink(email, properties.emailUrl(token), (int) properties.lifetime().toMinutes());
        } catch (RuntimeException e) {
            log.error("Could not send the sign-in link to {}: {}", email, e.toString());
        }
    }

    public AuthService.TokenResponse verifyLink(String rawToken, String codeVerifier) {
        if (!SignInLinkPageController.looksLikeToken(rawToken) || codeVerifier == null || codeVerifier.isBlank()
                || codeVerifier.length() > 200) {
            throw new InvalidLinkException();
        }
        String hash = AuthService.sha256(rawToken.trim());
        EmailLoginLink saved = links.findById(hash).orElseThrow(InvalidLinkException::new);
        if (!clock.instant().isBefore(saved.getExpiresAt())) {
            links.deleteLink(hash);
            throw new InvalidLinkException();
        }
        boolean sameApp = MessageDigest.isEqual(
                AuthService.sha256(codeVerifier.trim()).getBytes(StandardCharsets.UTF_8),
                saved.getCodeChallenge().getBytes(StandardCharsets.UTF_8));
        // A wrong secret does not use the link up: the real app can still open it.
        if (!sameApp || links.deleteLink(hash) != 1) {
            throw new InvalidLinkException();
        }
        return auth.issueTokensFor(auth.findOrCreateUser(saved.getEmail()));
    }
}
