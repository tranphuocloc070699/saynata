package com.englishtranslation.backend.auth;

import com.englishtranslation.backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in app user (Bearer access token). SecurityConfig requires the USER role for /api/me. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService service;

    public MeController(AuthService service) {
        this.service = service;
    }

    @GetMapping
    public AuthService.UserView me(Authentication authentication) {
        User user = service.currentUser(userId(authentication));
        return new AuthService.UserView(user.getId(), user.getEmail());
    }

    /** Deletes the account and all its tokens. The App Store requires this. */
    @DeleteMapping
    public ResponseEntity<Void> delete(Authentication authentication) {
        service.deleteAccount(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
