package com.englishtranslation.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.RateLimiter;
import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.Matchers;
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
import tools.jackson.databind.json.JsonMapper;

/** The emailed sign-in link end to end, with a fake email sender that remembers the link it was given. */
@SpringBootTest(properties = { "app.claude.api-key=test-key", "app.auth.link.public-base-url=https://api.test.example/" })
@AutoConfigureMockMvc
class SignInLinkTest {

    static final String EMAIL = "learner@example.com";
    static final String VERIFIER = "the-secret-the-app-keeps-until-the-link-comes-back";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository users;
    @Autowired
    EmailLoginLinkRepository links;
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
        links.deleteAll();
        users.deleteAll();
        rateLimiter.reset();
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions requestLink(String email) throws Exception {
        return postJson("/api/auth/email-link", Map.of("email", email, "codeChallenge", AuthService.sha256(VERIFIER)));
    }

    /** Asks for a link and returns the token from the address the fake sender was told to email. */
    private String requestLinkAndReadToken(String email) throws Exception {
        requestLink(email).andExpect(status().isNoContent());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendSignInLink(eq(email.trim().toLowerCase()), link.capture(), anyInt());
        assertThat(link.getValue()).startsWith("https://api.test.example/auth/link?token=");
        return link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());
    }

    private ResultActions verifyLink(String token, String verifier) throws Exception {
        return postJson("/api/auth/email-link/verify", Map.of("token", token, "codeVerifier", verifier));
    }

    @Test
    void theLinkSignsInOnceAndOnlyItsHashIsSaved() throws Exception {
        String token = requestLinkAndReadToken("Learner@Example.com ");

        EmailLoginLink saved = links.findAll().get(0);
        assertThat(saved.getTokenHash()).isEqualTo(AuthService.sha256(token)).isNotEqualTo(token);
        assertThat(saved.getExpiresAt()).isBefore(Instant.now().plusSeconds(16 * 60));

        verifyLink(token, VERIFIER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        verifyLink(token, VERIFIER)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_link"));
    }

    @Test
    void anotherAppWithoutTheSecretCannotUseTheLinkAndDoesNotUseItUp() throws Exception {
        String token = requestLinkAndReadToken(EMAIL);

        verifyLink(token, "a-guess").andExpect(status().isUnauthorized());
        verifyLink(token, VERIFIER).andExpect(status().isOk());
    }

    @Test
    void aNewLinkReplacesTheOldOne() throws Exception {
        String first = requestLinkAndReadToken(EMAIL);
        String second = requestLinkAndReadToken(EMAIL);

        assertThat(links.count()).isEqualTo(1);
        verifyLink(first, VERIFIER).andExpect(status().isUnauthorized());
        verifyLink(second, VERIFIER).andExpect(status().isOk());
    }

    @Test
    void anOldLinkIsRefused() throws Exception {
        String token = "an-old-token-an-old-token-an-old-token-1234";
        links.save(new EmailLoginLink(AuthService.sha256(token), EMAIL, AuthService.sha256(VERIFIER),
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60)));

        verifyLink(token, VERIFIER).andExpect(status().isUnauthorized());
        assertThat(links.count()).isZero();
    }

    @Test
    void anExistingUserKeepsTheSameAccount() throws Exception {
        User existing = users.save(User.appUser(EMAIL));

        verifyLink(requestLinkAndReadToken(EMAIL), VERIFIER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existing.getId()));
    }

    @Test
    void badRequestsAreRefused() throws Exception {
        postJson("/api/auth/email-link", Map.of("email", "not-an-email", "codeChallenge", AuthService.sha256(VERIFIER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_email"));
        postJson("/api/auth/email-link", Map.of("email", EMAIL, "codeChallenge", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_code_challenge"));
        verifyLink("<script>", VERIFIER).andExpect(status().isUnauthorized());
    }

    @Test
    void emailFailureStillAnswers204() throws Exception {
        doThrow(new RuntimeException("Resend is down")).when(emailSender).sendSignInLink(eq(EMAIL), anyString(), anyInt());

        requestLink(EMAIL).andExpect(status().isNoContent());
    }

    @Test
    void linksAndCodesShareTheEmailLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestLink(EMAIL).andExpect(status().isNoContent());
        }
        requestLink(EMAIL).andExpect(status().isTooManyRequests());
        postJson("/api/auth/email-code", Map.of("email", EMAIL)).andExpect(status().isTooManyRequests());
    }

    // ---- the page in the email ----

    @Test
    void theLinkPageOpensTheAppAndDoesNotUseTheLinkUp() throws Exception {
        String token = requestLinkAndReadToken(EMAIL);

        mockMvc.perform(get("/auth/link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")))
                .andExpect(content().string(Matchers.containsString("saynata://sign-in?token=" + token)));

        verifyLink(token, VERIFIER).andExpect(status().isOk());
    }

    @Test
    void aBrokenLinkShowsAHelpPage() throws Exception {
        mockMvc.perform(get("/auth/link").param("token", "\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("does not work")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("alert(1)"))));
    }

    @Test
    void theAppleAppSiteAssociationIsOffUntilTheAppIdIsSet() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association")).andExpect(status().isNotFound());

        var controller = new SignInLinkPageController(new SignInLinkProperties(java.time.Duration.ofMinutes(15),
                "https://api.test.example", "saynata", java.util.List.of("TEAM123.com.example.app")));
        assertThat(json.writeValueAsString(controller.appleAppSiteAssociation().getBody()))
                .contains("TEAM123.com.example.app")
                .contains("/auth/link*");
    }
}
