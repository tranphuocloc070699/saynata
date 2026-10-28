package com.saynata.backend.word;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP only: read the request, call the service, build the response. No
 * business rules here.
 */
@RestController
@RequestMapping("/api/words")
public class WordController {

    private final WordService service;

    public WordController(WordService service) {
        this.service = service;
    }

    /** POST /api/words -> 201 Created + Location header */
    @PostMapping
    public ResponseEntity<WordResponse> create(@Valid @RequestBody WordRequest request) {
        WordResponse created = service.create(request);
        // The response header will shows api/words/{id} -> app or web can get data from
        // it
        return ResponseEntity.created(URI.create("/api/words/" + created.id())).body(created);
    }

    /** GET /api/words/1 */
    @GetMapping("/{id}")
    public WordResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** GET /api/words?headword=run (case does not matter) */
    @GetMapping
    public WordResponse findByHeadword(@RequestParam String headword) {
        return service.findByHeadword(headword);
    }
}
