package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.common.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Checks a Google or Apple ID token, the way both providers ask
 * (https://developers.google.com/identity/sign-in/ios/backend-auth,
 * https://developer.apple.com/documentation/signinwithapple/verifying-a-user):
 * <ol>
 * <li>the RS256 signature, with the provider's public keys (JWKS);</li>
 * <li>"iss" is the provider, "aud" is one of our client IDs, "exp" is in the future (1 minute of clock skew);</li>
 * <li>"nonce" is the SHA-256 (hex) of the random nonce the app made for this one sign-in, so a token stolen from
 * another sign-in cannot be replayed here.</li>
 * </ol>
 */
@Service
public class IdTokenVerifier {

    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
    private static final int MAX_TOKEN_LENGTH = 8192;

    /** Who the provider says signed in. {@code email} is null when the provider did not confirm one. */
    public record VerifiedIdentity(String subject, String email) {
    }

    private final IdTokenKeys keys;
    private final SocialAuthProperties properties;
    private final Clock clock;

    public IdTokenVerifier(IdTokenKeys keys, SocialAuthProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    public VerifiedIdentity verify(SocialProvider provider, String idToken, String rawNonce) {
        List<String> audiences = properties.clientIdsFor(provider);
        if (audiences.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "provider_not_configured",
                    "This sign-in option is not set up yet. Use your email.");
        }
        if (idToken == null || idToken.isBlank() || idToken.length() > MAX_TOKEN_LENGTH
                || rawNonce == null || rawNonce.isBlank()) {
            throw new InvalidIdTokenException();
        }

        Jwt jwt;
        try {
            jwt = decoder(provider, audiences).decode(idToken.trim());
        } catch (JwtException e) {
            throw new InvalidIdTokenException();
        }

        String expectedNonce = AuthService.sha256(rawNonce.trim());
        String nonce = jwt.getClaimAsString("nonce");
        if (nonce == null || !java.security.MessageDigest.isEqual(
                expectedNonce.getBytes(StandardCharsets.UTF_8), nonce.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidIdTokenException();
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new InvalidIdTokenException();
        }
        String email = jwt.getClaimAsString("email");
        // Google sends a boolean, Apple sometimes the text "true".
        boolean verified = "true".equalsIgnoreCase(String.valueOf(jwt.getClaims().get("email_verified")));
        return new VerifiedIdentity(subject, email != null && !email.isBlank() && verified ? email : null);
    }

    /** Built per call: it is cheap, and the keys (the costly part) are cached by {@link IdTokenKeys}. */
    private NimbusJwtDecoder decoder(SocialProvider provider, List<String> audiences) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys.keysFor(provider)));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
            // The claims are checked by the Spring validators below, with our clock.
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> hasExpiry = new JwtClaimValidator<Object>(JwtClaimNames.EXP, exp -> exp != null);
        OAuth2TokenValidator<Jwt> issuer = new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                iss -> iss != null && provider.issuers().contains(iss.toString()));
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(audiences::contains));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(hasExpiry, timestamps, issuer, audience)));
        return decoder;
    }
}
