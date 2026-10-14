package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.user.User;
import com.englishtranslation.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login, "who am I" and logout for the admin web app. The login lives in the saynata_admin cookie. */
@RestController
@RequestMapping("/api/admin/auth")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminAuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenService tokens;
    private final LoginRateLimiter rateLimiter;
    private final AdminProperties properties;
    /** Compared with the password when the email is unknown, so "no such email" takes as long as "wrong password". */
    private final String dummyHash;

    public AdminAuthController(UserRepository users, PasswordEncoder passwordEncoder, AdminTokenService tokens,
            LoginRateLimiter rateLimiter, AdminProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    /** What the admin web app needs to show who is logged in. */
    public record AdminInfo(String email, String displayName) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Try again in 15 minutes.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
        }

        User user = users.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        // Same answer for "no such email", "wrong password" and "not an admin", so nothing is revealed.
        if (user == null || !passwordOk || !user.isAdmin()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        String token = tokens.createToken(user.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(token, AdminTokenService.LIFETIME.toSeconds()).toString())
                .body(new AdminInfo(user.getEmail(), user.getDisplayName()));
    }

    @GetMapping("/me")
    public AdminInfo me(Authentication authentication) {
        User user = users.findByEmail(authentication.getName()).orElseThrow();
        return new AdminInfo(user.getEmail(), user.getDisplayName());
    }

    /** Clears the cookie. Open to everyone: logging out when already logged out is fine. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .build();
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(AdminCookieFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/admin")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
