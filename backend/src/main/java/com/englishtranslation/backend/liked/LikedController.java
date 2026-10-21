package com.englishtranslation.backend.liked;

import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.CallerContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The liked words and sentences of the signed-in user (TASK-93.1, doc-17). A guest gets 401 sign_in_required.
 * The user comes from CallerContext (set by BearerTokenFilter), not from SecurityConfig, so a guest gets a
 * problem-details answer with a code the app can read.
 */
@RestController
@RequestMapping("/api/liked")
public class LikedController {

    private final LikedService service;

    public LikedController(LikedService service) {
        this.service = service;
    }

    @GetMapping
    public LikedListResponse list() {
        return new LikedListResponse(service.list(userId()));
    }

    @PutMapping
    public LikedItemView like(@RequestBody LikedRequest request) {
        return service.like(userId(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlike(@RequestParam String kind, @RequestParam String text) {
        service.unlike(userId(), kind, text);
        return ResponseEntity.noContent().build();
    }

    private static long userId() {
        return CallerContext.userId().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "sign_in_required", "Sign in to like words and sentences."));
    }
}
