package com.englishtranslation.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.RateLimiter;
import com.englishtranslation.backend.user.Role;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Email code sign-in end to end, with a fake email sender (a mock that remembers the code it was given). */
@SpringBootTest(properties = "app.claude.api-key=test-key")
@AutoConfigureMockMvc
class AuthFlowTest {

    static final String EMAIL = "learner@example.com";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    EmailLoginCodeRepository codes;
    @Autowired
    RefreshTokenRepository refreshTokens;
    @Autowired
    AuthService authService;
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
        codes.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions requestCode(String email) throws Exception {
        return postJson("/api/auth/email-code", "{\"email\": \"" + email + "\"}");
    }

    /** Asks for a code and returns what the fake sender was told to email. */
    private String requestCodeAndRead(String email) throws Exception {
        requestCode(email).andExpect(status().isNoContent());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendSignInCode(eq(email.trim().toLowerCase()), code.capture(), anyInt());
        return code.getValue();
    }

    private ResultActions verifyCode(String email, String code) throws Exception {
        return postJson("/api/auth/verify", "{\"email\": \"" + email + "\", \"code\": \"" + code + "\"}");
    }

    private JsonNode signIn(String email) throws Exception {
        String code = requestCodeAndRead(email);
        String body = verifyCode(email, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String wrongCodeFor(String right) {
        return right.equals("000000") ? "111111" : "000000";
    }

    // ---- email code ----

    @Test
    void emailCodeAlwaysAnswers204ForKnownAndUnknownEmails() throws Exception {
        users.save(User.appUser("known@example.com"));

        requestCode("known@example.com").andExpect(status().isNoContent()).andExpect(content204());
        requestCode("stranger@example.com").andExpect(status().isNoContent()).andExpect(content204());
    }

    private static org.springframework.test.web.servlet.ResultMatcher content204() {
        return result -> assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void theCodeIsSixDigitsSentByEmailAndOnlyAKeyedHashIsSaved() throws Exception {
        String code = requestCodeAndRead("Learner@Example.com "); // trimmed and lower-cased

        assertThat(code).matches("\\d{6}");
        EmailLoginCode saved = codes.findById(EMAIL).orElseThrow();
        assertThat(saved.getCodeHash()).matches("[0-9a-f]{64}").doesNotContain(code).isNotEqualTo(code);
        // It is not a plain SHA-256 of the code, which could be guessed from 1,000,000 possibilities.
        assertThat(saved.getCodeHash()).isNotEqualTo(AuthService.sha256(code));
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plusSeconds(9 * 60)).isBefore(Instant.now().plusSeconds(11 * 60));
    }

    @Test
    void aBadEmailGets400() throws Exception {
        requestCode("not-an-email").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_email"));
        requestCode("two@@example.com").andExpect(status().isBadRequest());
        requestCode("a b@example.com").andExpect(status().isBadRequest());
    }

    @Test
    void aSecondCodeReplacesTheFirstSoOnlyOneCodeWorksAtATime() throws Exception {
        String first = requestCodeAndRead(EMAIL);
        String second = requestCodeAndRead(EMAIL);
        if (first.equals(second)) { // 1 in 1,000,000: ask again so the test can tell them apart
            second = requestCodeAndRead(EMAIL);
        }

        assertThat(codes.count()).isEqualTo(1);
        verifyCode(EMAIL, first).andExpect(status().isUnauthorized());
        verifyCode(EMAIL, second).andExpect(status().isOk());
    }

    @Test
    void emailFailureStillAnswers204() throws Exception {
        doThrow(new RuntimeException("SES is down")).when(emailSender)
                .sendSignInCode(eq(EMAIL), anyString(), anyInt());

        requestCode(EMAIL).andExpect(status().isNoContent());
    }

    // ---- verify ----

    @Test
    void verifyCreatesTheUserOnFirstSignInAndReturnsTokens() throws Exception {
        JsonNode result = signIn(EMAIL);

        assertThat(result.get("accessToken").asString()).isNotBlank();
        assertThat(result.get("refreshToken").asString()).hasSizeGreaterThan(30);
        assertThat(result.get("expiresIn").asLong()).isEqualTo(3600);
        assertThat(result.get("user").get("email").asString()).isEqualTo(EMAIL);
        User user = users.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getRoles()).containsExactly(Role.USER);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(result.get("user").get("id").asLong()).isEqualTo(user.getId());
    }

    @Test
    void theSecondSignInUsesTheSameUser() throws Exception {
        JsonNode first = signIn(EMAIL);
        JsonNode second = signIn(EMAIL);

        assertThat(users.count()).isEqualTo(1);
        assertThat(second.get("user").get("id").asLong()).isEqualTo(first.get("user").get("id").asLong());
    }

    @Test
    void aCodeWorksOnlyOnce() throws Exception {
        String code = requestCodeAndRead(EMAIL);

        verifyCode(EMAIL, code).andExpect(status().isOk());
        verifyCode(EMAIL, code).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_code"));
    }

    @Test
    void afterFiveWrongTriesEvenTheRightCodeIsRefused() throws Exception {
        String code = requestCodeAndRead(EMAIL);
        String wrong = wrongCodeFor(code);

        for (int i = 0; i < 5; i++) {
            verifyCode(EMAIL, wrong).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_code"));
        }

        verifyCode(EMAIL, code).andExpect(status().isUnauthorized());
        assertThat(users.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void theRightCodeStillWorksAfterFourWrongTries() throws Exception {
        String code = requestCodeAndRead(EMAIL);
        for (int i = 0; i < 4; i++) {
            verifyCode(EMAIL, wrongCodeFor(code)).andExpect(status().isUnauthorized());
        }

        verifyCode(EMAIL, code).andExpect(status().isOk());
    }

    @Test
    void anExpiredCodeIsRefused() throws Exception {
        // A code that expired a minute ago, saved the way the service saves it.
        Instant now = Instant.now();
        codes.save(new EmailLoginCode(EMAIL, authService.hashCode(EMAIL, "123456"), now.minusSeconds(660),
                now.minusSeconds(60)));

        verifyCode(EMAIL, "123456").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_code"));
        assertThat(codes.findById(EMAIL)).isEmpty();
    }

    @Test
    void noCodeAtAllAndMalformedCodesAreRefused() throws Exception {
        verifyCode(EMAIL, "123456").andExpect(status().isUnauthorized());
        requestCodeAndRead(EMAIL);
        verifyCode(EMAIL, "12345").andExpect(status().isUnauthorized());
        verifyCode(EMAIL, "abcdef").andExpect(status().isUnauthorized());
    }

    // ---- refresh and sign-out ----

    @Test
    void refreshGivesANewPairAndTheOldRefreshTokenStopsWorking() throws Exception {
        JsonNode first = signIn(EMAIL);
        String oldRefresh = first.get("refreshToken").asString();

        String body = postJson("/api/auth/refresh", "{\"refreshToken\": \"" + oldRefresh + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode second = json.readTree(body);

        assertThat(second.get("refreshToken").asString()).isNotEqualTo(oldRefresh);
        assertThat(second.get("user").get("email").asString()).isEqualTo(EMAIL);
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + second.get("accessToken").asString()))
                .andExpect(status().isOk());
        // The used token is dead.
        postJson("/api/auth/refresh", "{\"refreshToken\": \"" + oldRefresh + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid_refresh_token"));
    }

    @Test
    void usingAnOldRefreshTokenAgainEndsAllSessionsOfThatUser() throws Exception {
        JsonNode first = signIn(EMAIL);
        String oldRefresh = first.get("refreshToken").asString();
        String newRefresh = json.readTree(postJson("/api/auth/refresh", "{\"refreshToken\": \"" + oldRefresh + "\"}")
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asString();

        postJson("/api/auth/refresh", "{\"refreshToken\": \"" + oldRefresh + "\"}").andExpect(status().isUnauthorized());

        // The thief used the old token, so the new one is revoked too: the real user signs in again.
        postJson("/api/auth/refresh", "{\"refreshToken\": \"" + newRefresh + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAHashOfTheRefreshTokenIsSaved() throws Exception {
        String refresh = signIn(EMAIL).get("refreshToken").asString();

        assertThat(refreshTokens.findAll()).hasSize(1);
        assertThat(refreshTokens.findByTokenHash(refresh)).isEmpty();
        assertThat(refreshTokens.findByTokenHash(AuthService.sha256(refresh))).isPresent();
    }

    @Test
    void anExpiredRefreshTokenIsRefused() throws Exception {
        User user = users.save(User.appUser(EMAIL));
        Instant now = Instant.now();
        refreshTokens.save(new RefreshToken(user.getId(), AuthService.sha256("old-token"), now.minusSeconds(100), now.minusSeconds(1)));

        postJson("/api/auth/refresh", "{\"refreshToken\": \"old-token\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void signOutRevokesTheRefreshToken() throws Exception {
        String refresh = signIn(EMAIL).get("refreshToken").asString();

        postJson("/api/auth/sign-out", "{\"refreshToken\": \"" + refresh + "\"}").andExpect(status().isNoContent());

        postJson("/api/auth/refresh", "{\"refreshToken\": \"" + refresh + "\"}").andExpect(status().isUnauthorized());
        // Signing out again, or with a made-up token, is not an error.
        postJson("/api/auth/sign-out", "{\"refreshToken\": \"" + refresh + "\"}").andExpect(status().isNoContent());
        postJson("/api/auth/sign-out", "{\"refreshToken\": \"never-existed\"}").andExpect(status().isNoContent());
    }

    // ---- /api/me ----

    @Test
    void meNeedsABearerToken() throws Exception {
        String access = signIn(EMAIL).get("accessToken").asString();

        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void anAccessTokenDoesNotOpenTheAdminApiAndTheAdminCookieDoesNotOpenMe() throws Exception {
        String access = signIn(EMAIL).get("accessToken").asString();

        mockMvc.perform(get("/api/admin/words").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    // ---- delete the account ----

    @Test
    void deleteMeRemovesTheAccountItsTokensAndTheAccessToken() throws Exception {
        JsonNode signedIn = signIn(EMAIL);
        String access = signedIn.get("accessToken").asString();
        String refresh = signedIn.get("refreshToken").asString();
        assertThat(refreshTokens.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());

        assertThat(users.findByEmail(EMAIL)).isEmpty();
        assertThat(refreshTokens.count()).isZero();
        // The access token is still signed and not expired, but its user is gone.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", "{\"refreshToken\": \"" + refresh + "\"}").andExpect(status().isUnauthorized());
        // The same email can sign up again later, as a new user.
        assertThat(signIn(EMAIL).get("user").get("id").asLong()).isNotEqualTo(signedIn.get("user").get("id").asLong());
    }

    @Test
    void deleteMeNeedsASignedInUser() throws Exception {
        mockMvc.perform(delete("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminAccountCannotBeDeletedThroughMe() throws Exception {
        User admin = users.save(new User("boss@example.com", "{bcrypt}x", "Boss", Role.ADMIN));
        // The admin signs in with an email code too; then the account has both roles.
        JsonNode signedIn = signIn("boss@example.com");
        assertThat(users.findByEmail("boss@example.com").orElseThrow().getRoles()).contains(Role.ADMIN, Role.USER);

        mockMvc.perform(delete("/api/me").header("Authorization", "Bearer " + signedIn.get("accessToken").asString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("admin_account"));
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void anAppUserCannotLogInToTheAdminWithAnEmptyPassword() throws Exception {
        users.save(User.appUser(EMAIL));

        postJson("/api/admin/auth/login", "{\"email\": \"" + EMAIL + "\", \"password\": \"anything-at-all\"}")
                .andExpect(status().isUnauthorized());
    }

    // ---- rate limits on the email code ----

    @Test
    void thirdCodeForOneEmailIn15MinutesIsAllowedAndTheFourthGets429() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestCode(EMAIL).andExpect(status().isNoContent());
        }

        requestCode(EMAIL).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void sixthCodeFromOneIpIn1HourGets429EvenForDifferentEmails() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/email-code").with(fromIp("203.0.113.9")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"user" + i + "@example.com\"}")).andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/auth/email-code").with(fromIp("203.0.113.9")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"user99@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
        // Another IP is not affected.
        mockMvc.perform(post("/api/auth/email-code").with(fromIp("203.0.113.10")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"user99@example.com\"}")).andExpect(status().isNoContent());
    }
}
