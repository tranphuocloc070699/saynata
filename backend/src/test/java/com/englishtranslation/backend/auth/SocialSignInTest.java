package com.englishtranslation.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.RateLimiter;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sign in with Google and Apple end to end. The "provider" is a test RSA key: tokens are signed with it here and
 * the backend checks them with its public half, so no call ever goes to Google or Apple.
 */
@SpringBootTest(properties = { "app.claude.api-key=test-key", "app.auth.social.google-client-ids=test-google-ios",
        "app.auth.social.apple-client-ids=com.example.app" })
@AutoConfigureMockMvc
class SocialSignInTest {

    static final RSAKey PROVIDER_KEY = newKey("provider");
    static final RSAKey OTHER_KEY = newKey("other");
    static final String NONCE = "a-random-nonce-made-by-the-app";

    @TestConfiguration
    static class FakeProviderKeys {
        @Bean
        @Primary
        IdTokenKeys fakeIdTokenKeys() {
            return provider -> new ImmutableJWKSet<>(new JWKSet(PROVIDER_KEY.toPublicJWK()));
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    UserIdentityRepository identities;
    @Autowired
    RefreshTokenRepository refreshTokens;
    @Autowired
    RateLimiter rateLimiter;
    @Autowired
    JsonMapper json;

    @MockitoBean
    EmailSender emailSender;
    @MockitoBean
    ClaudeService claude;

    @BeforeEach
    void clean() {
        refreshTokens.deleteAll();
        identities.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private static RSAKey newKey(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A Google ID token for the test client; {@code change} edits the claims for the bad cases. */
    static String googleToken(String subject, String email, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience("test-google-ios")
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", true)
                .claim("nonce", AuthService.sha256(NONCE))
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)));
        change.accept(claims);
        return sign(claims.build(), PROVIDER_KEY);
    }

    static String appleToken(String subject, String email, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .audience("com.example.app")
                .subject(subject)
                .claim("nonce", AuthService.sha256(NONCE))
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)));
        if (email != null) {
            claims.claim("email", email).claim("email_verified", "true"); // Apple sends text, not a boolean
        }
        change.accept(claims);
        return sign(claims.build(), PROVIDER_KEY);
    }

    static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private ResultActions signIn(String provider, String idToken, String nonce) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("idToken", idToken, "nonce", nonce));
        return mockMvc.perform(post("/api/auth/" + provider).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode okBody(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ---- Google ----

    @Test
    void googleSignInCreatesTheUserAndReturnsTheSameTokensAsTheEmailCode() throws Exception {
        JsonNode result = okBody(signIn("google", googleToken("g-1", "Learner@Gmail.com", c -> { }), NONCE));

        assertThat(result.get("accessToken").asString()).isNotBlank();
        assertThat(result.get("refreshToken").asString()).hasSizeGreaterThan(30);
        assertThat(result.get("user").get("email").asString()).isEqualTo("learner@gmail.com");
        assertThat(identities.findByProviderAndSubject(SocialProvider.GOOGLE, "g-1")).isPresent();
    }

    @Test
    void theSameGoogleAccountFindsTheSameUserEvenAfterAnEmailChange() throws Exception {
        long first = okBody(signIn("google", googleToken("g-1", "old@gmail.com", c -> { }), NONCE))
                .get("user").get("id").asLong();
        long second = okBody(signIn("google", googleToken("g-1", "new@gmail.com", c -> { }), NONCE))
                .get("user").get("id").asLong();

        assertThat(second).isEqualTo(first);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void aUserWhoSignedInWithAnEmailCodeKeepsTheSameAccount() throws Exception {
        User existing = users.save(User.appUser("learner@gmail.com"));

        JsonNode result = okBody(signIn("google", googleToken("g-1", "learner@gmail.com", c -> { }), NONCE));

        assertThat(result.get("user").get("id").asLong()).isEqualTo(existing.getId());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void aTokenForAnotherAppIsRefused() throws Exception {
        signIn("google", googleToken("g-1", "a@gmail.com", c -> c.audience("someone-elses-app")), NONCE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_id_token"));
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        signIn("google", googleToken("g-1", "a@gmail.com", c -> c.issuer("https://evil.example.com")), NONCE)
                .andExpect(status().isUnauthorized());
        // An Apple-signed token sent to the Google endpoint is refused too.
        signIn("google", appleToken("a-1", "a@icloud.com", c -> c.audience("test-google-ios")), NONCE)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        signIn("google", googleToken("g-1", "a@gmail.com",
                c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600)))), NONCE)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongNonceIsRefused() throws Exception {
        signIn("google", googleToken("g-1", "a@gmail.com", c -> { }), "another-nonce")
                .andExpect(status().isUnauthorized());
        signIn("google", googleToken("g-1", "a@gmail.com", c -> c.claim("nonce", null)), NONCE)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(googleToken("g-1", "a@gmail.com", c -> { })).getJWTClaimsSet();
        signIn("google", sign(claims, OTHER_KEY), NONCE).andExpect(status().isUnauthorized());
        signIn("google", "not.a.token", NONCE).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedGoogleEmailCannotMakeAnAccount() throws Exception {
        signIn("google", googleToken("g-1", "a@gmail.com", c -> c.claim("email_verified", false)), NONCE)
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    // ---- Apple ----

    @Test
    void appleSignInWorksAndLaterTokensWithoutAnEmailFindTheSameUser() throws Exception {
        long first = okBody(signIn("apple", appleToken("a-1", "x1@privaterelay.appleid.com", c -> { }), NONCE))
                .get("user").get("id").asLong();
        long second = okBody(signIn("apple", appleToken("a-1", null, c -> { }), NONCE))
                .get("user").get("id").asLong();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void anUnknownAppleAccountWithoutAnEmailIsRefused() throws Exception {
        signIn("apple", appleToken("a-new", null, c -> { }), NONCE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_id_token"));
    }

    @Test
    void deletingTheAccountRemovesItsAppleIdentity() throws Exception {
        String access = okBody(signIn("apple", appleToken("a-1", "a@icloud.com", c -> { }), NONCE))
                .get("accessToken").asString();

        mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        assertThat(identities.count()).isZero();
        assertThat(users.count()).isZero();
    }

    // ---- not set up ----

    @Test
    void aProviderWithoutClientIdsAnswers503() {
        IdTokenVerifier verifier = new IdTokenVerifier(provider -> null,
                new SocialAuthProperties(List.of(), List.of(" "), "https://g", "https://a"), Clock.systemUTC());

        assertThatThrownBy(() -> verifier.verify(SocialProvider.GOOGLE, "token", NONCE))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> verifier.verify(SocialProvider.APPLE, "token", NONCE))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("provider_not_configured"));
    }
}
