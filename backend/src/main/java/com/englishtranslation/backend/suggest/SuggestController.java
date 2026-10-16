package com.englishtranslation.backend.suggest;

import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggest")
public class SuggestController {

    /** New cached phrases should show within minutes, and the same prefix is typed again often. */
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final SuggestService service;

    public SuggestController(SuggestService service) {
        this.service = service;
    }

    /**
     * GET /api/suggest?q=app&lang=vi: up to 8 words and phrases that start with q and already have an answer
     * (TASK-83.4). Open to guests, never calls Claude, rate limited with the other public reads.
     * lang (optional) keeps only phrases cached for that target language.
     */
    @GetMapping
    public ResponseEntity<SuggestResponse> suggest(@RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @Pattern(regexp = "[a-z]{2}") String lang) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .body(service.suggest(q, lang));
    }
}
