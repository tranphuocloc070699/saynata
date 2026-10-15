package com.englishtranslation.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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

/**
 * The short access token the app sends as "Authorization: Bearer ...": a signed JWT (HS256) that lives about
 * an hour. It holds only the user id. It is signed with its own key, so the admin cookie cannot be used as an
 * access token or the other way round.
 */
@Service
public class AccessTokenService {

    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "access";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(AuthKeys keys, AuthProperties properties, Clock clock) {
        SecretKey key = new SecretKeySpec(keys.derive("access-token"), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.properties = properties;
        this.clock = clock;
    }

    public long lifetimeSeconds() {
        return properties.accessTokenLifetime().toSeconds();
    }

    public String createToken(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, TYPE_ACCESS)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the user id, or -1 when the token is wrong, changed or expired. */
    public long readUserId(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!TYPE_ACCESS.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
                return -1;
            }
            return Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            return -1;
        }
    }
}
