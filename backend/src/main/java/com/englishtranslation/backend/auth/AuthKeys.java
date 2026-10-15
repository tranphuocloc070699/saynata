package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.admin.AdminProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The secret keys for accounts. One base secret gives separate keys (one per job), so the key that signs
 * access tokens is never the key that hashes email codes, and neither is the admin cookie key.
 *
 * The base secret is the first of: USER_JWT_SECRET, ADMIN_JWT_SECRET, a random one made at start-up.
 * Falling back is on purpose: the backend must always start (a missing variable must not break a deploy).
 * With the random fallback, sign-in works but everybody is signed out at each restart, and a warning is logged.
 */
@Component
public class AuthKeys {

    private static final Logger log = LoggerFactory.getLogger(AuthKeys.class);
    private static final int MIN_SECRET_LENGTH = 32;

    private final byte[] base;

    public AuthKeys(AuthProperties auth, AdminProperties admin) {
        if (isStrong(auth.userJwtSecret())) {
            this.base = auth.userJwtSecret().getBytes(StandardCharsets.UTF_8);
        } else if (isStrong(admin.jwtSecret())) {
            log.warn("USER_JWT_SECRET is not set. Sign-in keys are derived from ADMIN_JWT_SECRET. "
                    + "Set USER_JWT_SECRET (openssl rand -base64 48) to keep them separate.");
            this.base = admin.jwtSecret().getBytes(StandardCharsets.UTF_8);
        } else {
            log.warn("USER_JWT_SECRET and ADMIN_JWT_SECRET are not set. A random key is used, "
                    + "so everyone is signed out whenever the backend restarts. Set USER_JWT_SECRET.");
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.base = random;
        }
    }

    private static boolean isStrong(String secret) {
        return secret != null && secret.length() >= MIN_SECRET_LENGTH;
    }

    /** A 32-byte key for one job, for example "access-token" or "email-code". */
    public byte[] derive(String label) {
        return hmac(base, label.getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256, as 32 bytes. */
    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
