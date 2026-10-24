package com.englishtranslation.backend.lines;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The language line for short texts (TASK-106.2, doc-5). */
@RestController
@RequestMapping("/api/translate-lines")
public class LinesController {

    private final LineTranslationService service;

    public LinesController(LineTranslationService service) {
        this.service = service;
    }

    /** {@code cachedOnly=true}: only saved lines; a miss comes back with translation null and never calls Claude. */
    @PostMapping
    public TranslateLinesResponse translate(@RequestBody TranslateLinesRequest request,
            @RequestParam(defaultValue = "false") boolean cachedOnly) {
        return service.translate(request, cachedOnly);
    }
}
