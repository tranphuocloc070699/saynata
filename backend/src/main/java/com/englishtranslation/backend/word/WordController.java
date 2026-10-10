package com.englishtranslation.backend.word;

import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private final WordService service;

    public WordController(WordService service) {
        this.service = service;
    }

    /** One word: letters, apostrophes and hyphens only ("don't", "well-known"). */
    @GetMapping("/{word}")
    public WordResponse lookUp(@PathVariable @Pattern(regexp = "[\\p{L}'-]{1,64}") String word) {
        return service.lookUp(word);
    }
}
