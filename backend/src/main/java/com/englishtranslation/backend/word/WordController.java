package com.englishtranslation.backend.word;

import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class WordController {

    /** A bundled entry changes only by a rare admin edit or a deploy, so the app may keep it a day (TASK-83.4). */
    static final Duration BUNDLED_MAX_AGE = Duration.ofDays(1);
    /** A cached backend word may still get its picture or an admin review edit, so one hour. */
    static final Duration CACHED_MAX_AGE = Duration.ofHours(1);

    private final WordService service;

    public WordController(WordService service) {
        this.service = service;
    }

    /**
     * One word: letters, apostrophes and hyphens only ("don't", "well-known").
     * {@code cachedOnly=true} answers only from the bundled dictionary or the cache, else 404 not_cached;
     * it never causes a Claude call, so guests can use it while typing.
     */
    @GetMapping("/{word}")
    public ResponseEntity<WordResponse> lookUp(@PathVariable @Pattern(regexp = "[\\p{L}'-]{1,64}") String word,
            @RequestParam(defaultValue = "false") boolean cachedOnly) {
        WordAnswer answer = service.lookUp(word, cachedOnly);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        switch (answer.origin()) {
            case BUNDLED -> response.cacheControl(CacheControl.maxAge(BUNDLED_MAX_AGE).cachePublic());
            case CACHED -> response.cacheControl(CacheControl.maxAge(CACHED_MAX_AGE).cachePublic());
            // A new answer has no picture yet, and the word list changes on deploy: these keep Spring Security's
            // default "no-cache, no-store".
            case NEW, NOT_A_WORD -> {
            }
        }
        return response.body(answer.response());
    }
}
