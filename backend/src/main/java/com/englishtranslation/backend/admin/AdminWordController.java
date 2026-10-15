package com.englishtranslation.backend.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Admin API for words: the bundled dictionary (table dictionary_word) and the words Claude described
 * (table word_lookup). Shaped for Refine's simple-rest data provider: the list is a plain JSON array and the
 * total number of rows is in the X-Total-Count header. Ids are strings: "b-12" (bundled) and "w-45" (backend).
 */
@RestController
@RequestMapping("/api/admin/words")
public class AdminWordController {

    // Names Refine may send in _sort. AdminWordService turns each one into SQL columns.
    private static final Map<String, String> SORT_FIELDS = sortFields();

    private final AdminWordService service;

    public AdminWordController(AdminWordService service) {
        this.service = service;
    }

    /** cefr is a list: ?cefr=A1,A2 (or ?cefr=A1&cefr=A2). */
    @GetMapping
    public ResponseEntity<List<AdminWord>> list(
            @RequestParam(name = "_start", defaultValue = "0") int start,
            @RequestParam(name = "_end", defaultValue = "10") int end,
            @RequestParam(name = "_sort", defaultValue = "id") String sort,
            @RequestParam(name = "_order", defaultValue = "DESC") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "needs_review", required = false) Boolean needsReview,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "cefr", required = false) List<String> cefr,
            @RequestParam(name = "media", required = false) String media) {
        AdminWordService.WordPage page = service.list(
                AdminListParams.likePattern(q), needsReview, source, cefr, media,
                AdminListParams.pageable(start, end, sort, order, SORT_FIELDS));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.total()))
                .body(page.words());
    }

    @GetMapping("/{id}")
    public AdminWord get(@PathVariable String id) {
        return service.get(id);
    }

    /** Body: {"entry": {"senses": [...]}}. Other fields (id, word, ...) are ignored, so Refine can send the whole record. */
    @PutMapping("/{id}")
    public AdminWord update(@PathVariable String id, @RequestBody JsonNode body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/reviewed")
    public AdminWord markReviewed(@PathVariable String id) {
        return service.markReviewed(id);
    }

    /** Deletes a backend word (the next public lookup asks Claude again). A bundled word gives 400. */
    @DeleteMapping("/{id}")
    public AdminWord delete(@PathVariable String id) {
        return service.delete(id);
    }

    private static Map<String, String> sortFields() {
        Map<String, String> fields = new HashMap<>();
        for (String name : AdminWordService.sortNames()) {
            fields.put(name, name);
        }
        return Map.copyOf(fields);
    }
}
