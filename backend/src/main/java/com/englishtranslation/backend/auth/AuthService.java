package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.RateLimiter;
import com.englishtranslation.backend.limits.LimitProperties;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sign-in with an email code. No passwords.
 * <ol>
 * <li>{@link #requestCode}: make a 6-digit code, save only its keyed hash, email it.</li>
 * <li>{@link #verify}: check the code, create the user on the first sign-in, hand out an access token
 * (about 1 hour) and a refresh token (about 60 days).</li>
 * <li>{@link #refresh}: swap a refresh token for a new pair. The old one stops working (rotation).</li>
 * </ol>
 * There is no @Transactional on the methods on purpose: a wrong guess is counted by its own statement, and it
 * must stay counted when we then throw the "invalid code" error (a rollback would undo it).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration KEEP_EXPIRED_TOKENS = Duration.ofDays(7);

    /** The answer of verify and refresh. */
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserView user) {
    }

    public record UserView(Long id, String email) {
        static UserView of(User user) {
            return new UserView(user.getId(), user.getEmail());
        }
    }

    private final EmailLoginCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final AccessTokenService accessTokens;
    private final AuthProperties properties;
    private final AuthKeys keys;
    private final RateLimiter rateLimiter;
    private final LimitProperties limits;
    private final Clock clock;

    public AuthService(EmailLoginCodeRepository codes, RefreshTokenRepository refreshTokens, UserRepository users,
            EmailSender emailSender, AccessTokenService accessTokens, AuthProperties properties, AuthKeys keys,
            RateLimiter rateLimiter, LimitProperties limits, Clock clock) {
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.emailSender = emailSender;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.keys = keys;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Makes and sends a code. Returns nothing: the controller always answers 204, so nobody can learn
     * which emails have an account. A failed email is logged, never shown.
     */
    public void requestCode(String rawEmail, String clientIp) {
        String email = Emails.normalize(rawEmail);
        rateLimiter.acquireOrThrow(clientIp, limits.emailCodePerIpLimit());
        rateLimiter.acquireOrThrow(email, limits.emailCodePerEmailLimit());

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = clock.instant();
        // save() on the same email replaces the old row: one code per email at a time.
        codes.save(new EmailLoginCode(email, hashCode(email, code), now, now.plus(properties.codeLifetime())));
        try {
            emailSender.sendSignInCode(email, code, (int) properties.codeLifetime().toMinutes());
        } catch (RuntimeException e) {
            log.error("Could not send the sign-in code to {}: {}", email, e.toString());
        }
    }

    public TokenResponse verify(String rawEmail, String rawCode) {
        String email = Emails.normalize(rawEmail);
        if (rawCode == null || !rawCode.trim().matches("\\d{6}")) {
            throw new InvalidCodeException();
        }
        String code = rawCode.trim();

        EmailLoginCode saved = codes.findById(email).orElseThrow(InvalidCodeException::new);
        if (!clock.instant().isBefore(saved.getExpiresAt())) {
            codes.deleteCode(email);
            throw new InvalidCodeException();
        }
        // Count the guess first, atomically. 0 rows = all guesses used.
        if (codes.countGuess(email, properties.maxCodeTries()) == 0) {
            throw new InvalidCodeException();
        }
        boolean same = MessageDigest.isEqual(
                hashCode(email, code).getBytes(StandardCharsets.UTF_8), saved.getCodeHash().getBytes(StandardCharsets.UTF_8));
        // deleteCode returns 1 for exactly one request, so a code can be used only once, even in a race.
        if (!same || codes.deleteCode(email) != 1) {
            throw new InvalidCodeException();
        }

        User user = findOrCreateUser(email);
        refreshTokens.deleteExpiredBefore(clock.instant().minus(KEEP_EXPIRED_TOKENS));
        return issueTokens(user);
    }

    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken saved = findRefreshToken(rawRefreshToken).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (!now.isBefore(saved.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        if (refreshTokens.revoke(saved.getId(), now) == 0) {
            // This token was already used or signed out. Someone may have copied it, so end all sessions of the user.
            log.warn("A used refresh token was sent again. Revoking all tokens of user {}.", saved.getUserId());
            refreshTokens.revokeAllOf(saved.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(saved.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    /** Ends the session of this refresh token. An unknown token is fine: signing out twice is not an error. */
    public void signOut(String rawRefreshToken) {
        findRefreshToken(rawRefreshToken).ifPresent(token -> refreshTokens.revoke(token.getId(), clock.instant()));
    }

    public User currentUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "sign_in_required",
                "Sign in again."));
    }

    /** Deletes the account and every token of it (App Store rule). */
    public void deleteAccount(long userId) {
        User user = currentUser(userId);
        if (user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin_account",
                    "An admin account cannot be deleted here. Ask the developer.");
        }
        refreshTokens.deleteAllOf(userId);
        codes.deleteCode(user.getEmail());
        users.delete(user);
    }

    private User findOrCreateUser(String email) {
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            try {
                return users.saveAndFlush(User.appUser(email));
            } catch (DataIntegrityViolationException e) {
                // Two first sign-ins at the same moment: the other one created the user.
                return users.findByEmail(email).orElseThrow(() -> e);
            }
        }
        if (!user.getRoles().contains(Role.USER)) {
            user.addRole(Role.USER); // for example an admin who also uses the app
            return users.save(user);
        }
        return user;
    }

    private TokenResponse issueTokens(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(user.getId(), sha256(refreshToken), now,
                now.plus(properties.refreshTokenLifetime())));
        return new TokenResponse(accessTokens.createToken(user.getId()), refreshToken, accessTokens.lifetimeSeconds(),
                UserView.of(user));
    }

    private java.util.Optional<RefreshToken> findRefreshToken(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            return java.util.Optional.empty();
        }
        return refreshTokens.findByTokenHash(sha256(raw.trim()));
    }

    /** Keyed hash of the code. A plain hash of 6 digits could be guessed in a second, so the server key is mixed in. */
    String hashCode(String email, String code) {
        byte[] mac = AuthKeys.hmac(keys.derive("email-code"), (email + ":" + code).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac);
    }

    /** A refresh token is 256 random bits, so a plain SHA-256 is enough to store it. */
    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
