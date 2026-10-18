package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.limits.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in: with an email code, an emailed link, Google or Apple. All calls are open (no token needed); the app calls
 * them before it has one. Every sign-in answers with the same {@link AuthService.TokenResponse}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final SignInLinkService links;
    private final SocialSignInService social;

    public AuthController(AuthService service, SignInLinkService links, SocialSignInService social) {
        this.service = service;
        this.links = links;
        this.social = social;
    }

    public record EmailCodeRequest(@NotBlank String email) {
    }

    public record VerifyRequest(@NotBlank String email, @NotBlank String code) {
    }

    public record TokenRequest(@NotBlank String refreshToken) {
    }

    /** {@code codeChallenge}: the SHA-256 (hex) of a secret the app keeps until the link comes back. */
    public record EmailLinkRequest(@NotBlank String email, @NotBlank String codeChallenge) {
    }

    public record EmailLinkVerifyRequest(@NotBlank String token, @NotBlank String codeVerifier) {
    }

    /** {@code nonce}: the raw nonce; the ID token holds its SHA-256 (hex). */
    public record IdTokenRequest(@NotBlank String idToken, @NotBlank String nonce) {
    }

    /** Always 204, whether or not the email has an account. Bad format gives 400, too many requests 429. */
    @PostMapping("/email-code")
    public ResponseEntity<Void> sendCode(@Valid @RequestBody EmailCodeRequest request, HttpServletRequest http) {
        service.requestCode(request.email(), ClientIp.of(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public AuthService.TokenResponse verify(@Valid @RequestBody VerifyRequest request) {
        return service.verify(request.email(), request.code());
    }

    /** Like /email-code: always 204. The email holds a link to {publicBaseUrl}/auth/link that opens the app. */
    @PostMapping("/email-link")
    public ResponseEntity<Void> sendLink(@Valid @RequestBody EmailLinkRequest request, HttpServletRequest http) {
        links.requestLink(request.email(), request.codeChallenge(), ClientIp.of(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-link/verify")
    public AuthService.TokenResponse verifyLink(@Valid @RequestBody EmailLinkVerifyRequest request) {
        return links.verifyLink(request.token(), request.codeVerifier());
    }

    @PostMapping("/google")
    public AuthService.TokenResponse google(@Valid @RequestBody IdTokenRequest request) {
        return social.signIn(SocialProvider.GOOGLE, request.idToken(), request.nonce());
    }

    @PostMapping("/apple")
    public AuthService.TokenResponse apple(@Valid @RequestBody IdTokenRequest request) {
        return social.signIn(SocialProvider.APPLE, request.idToken(), request.nonce());
    }

    @PostMapping("/refresh")
    public AuthService.TokenResponse refresh(@Valid @RequestBody TokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(@Valid @RequestBody TokenRequest request) {
        service.signOut(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
