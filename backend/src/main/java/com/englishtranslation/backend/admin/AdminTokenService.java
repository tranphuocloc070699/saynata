package com.englishtranslation.backend.admin;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Makes and checks the login token: a signed JWT (HS256) that lives 8 hours. */
@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminTokenService {

    public static final Duration LIFETIME = Duration.ofHours(8);
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public AdminTokenService(AdminProperties properties) {
        String secret = properties.jwtSecret();
        // Fail fast: a missing secret must stop the start-up, not show up as a login error later.
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("Set ADMIN_JWT_SECRET to at least " + MIN_SECRET_LENGTH
                    + " characters (for example: openssl rand -base64 48), or set ADMIN_ENABLED=false.");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** The token holds only the user id. Roles are read from the database on every request. */
    public String createToken(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the user id, or -1 when the token is wrong, changed or expired. */
    public long readUserId(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            return -1;
        }
    }
}
