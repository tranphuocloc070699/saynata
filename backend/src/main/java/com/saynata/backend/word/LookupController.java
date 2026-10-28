package com.saynata.backend.word;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/lookup?q=serendipity : the word from our database, or a new one made by the LLM. */
@RestController
@RequestMapping("/api/lookup")
public class LookupController {

    private final WordLookupService service;

    public LookupController(WordLookupService service) {
        this.service = service;
    }

    @GetMapping
    public LookupResponse lookup(@RequestParam String q, HttpServletRequest request) {
        // Behind a proxy this is the proxy's address unless server.forward-headers-strategy is set (see lesson notes).
        return service.lookup(q, request.getRemoteAddr());
    }
}
