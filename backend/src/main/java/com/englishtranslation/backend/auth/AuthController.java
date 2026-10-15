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

/** Sign in with an email code. All four calls are open (no token needed); the app calls them before it has one. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    public record EmailCodeRequest(@NotBlank String email) {
    }

    public record VerifyRequest(@NotBlank String email, @NotBlank String code) {
    }

    public record TokenRequest(@NotBlank String refreshToken) {
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
