package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.word.WordEntry;
import com.englishtranslation.backend.word.WordLookup;
import com.englishtranslation.backend.word.WordLookupRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admin API for the words Claude described (table word_lookup). Shaped for Refine's simple-rest data provider:
 * the list is a plain JSON array and the total number of rows is in the X-Total-Count header.
 */
@RestController
@RequestMapping("/api/admin/words")
public class AdminWordController {

    private static final Logger log = LoggerFactory.getLogger(AdminWordController.class);

    // Names Refine may send in _sort, and the entity field each one means.
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "word", "word",
            "needsReview", "needsReview",
            "model", "model",
            "promptVersion", "promptVersion",
            "createdAt", "createdAt");

    /** One word as the admin web app sees it. entry has the same shape as the public GET /api/words/{word}. */
    public record AdminWord(Long id, String word, WordEntry entry, String model, String promptVersion,
            boolean needsReview, Instant createdAt) {
    }

    private final WordLookupRepository repository;
    private final JsonMapper jsonMapper;
    private final EntryParser parser;

    public AdminWordController(WordLookupRepository repository, JsonMapper jsonMapper, EntryParser parser) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.parser = parser;
    }

    @GetMapping
    public ResponseEntity<List<AdminWord>> list(
            @RequestParam(name = "_start", defaultValue = "0") int start,
            @RequestParam(name = "_end", defaultValue = "10") int end,
            @RequestParam(name = "_sort", defaultValue = "id") String sort,
            @RequestParam(name = "_order", defaultValue = "DESC") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "needs_review", required = false) Boolean needsReview) {
        Page<WordLookup> page = repository.search(
                AdminListParams.likePattern(q),
                needsReview != null,
                needsReview != null && needsReview,
                AdminListParams.pageable(start, end, sort, order, SORT_FIELDS));
        List<AdminWord> words = page.getContent().stream().map(this::toAdminWord).toList();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(words);
    }

    @GetMapping("/{id}")
    public AdminWord get(@PathVariable long id) {
        return toAdminWord(find(id));
    }

    /** Body: {"entry": {"senses": [...]}}. Other fields (id, word, ...) are ignored, so Refine can send the whole record. */
    @PutMapping("/{id}")
    public AdminWord update(@PathVariable long id, @RequestBody JsonNode body) {
        WordLookup lookup = find(id);
        WordEntry entry = parser.parseWordEntry(body);
        lookup.setEntryJson(jsonMapper.writeValueAsString(entry));
        return toAdminWord(repository.save(lookup));
    }

    /** The person looked at the word and it is fine: needsReview becomes false. */
    @PostMapping("/{id}/reviewed")
    public AdminWord markReviewed(@PathVariable long id) {
        WordLookup lookup = find(id);
        lookup.setNeedsReview(false);
        return toAdminWord(repository.save(lookup));
    }

    /** Deletes the row. The next public lookup of this word asks Claude again, so this is "regenerate". */
    @DeleteMapping("/{id}")
    public AdminWord delete(@PathVariable long id) {
        WordLookup lookup = find(id);
        repository.delete(lookup);
        return toAdminWord(lookup);
    }

    private WordLookup find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No word with id " + id + "."));
    }

    private AdminWord toAdminWord(WordLookup lookup) {
        WordEntry entry = null;
        try {
            entry = jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
        } catch (RuntimeException e) {
            // A broken old row must not break the whole list. The admin sees entry = null and can delete it.
            log.warn("word_lookup {} has entry_json that cannot be read: {}", lookup.getId(), e.toString());
        }
        return new AdminWord(lookup.getId(), lookup.getWord(), entry, lookup.getModel(), lookup.getPromptVersion(),
                lookup.isNeedsReview(), lookup.getCreatedAt());
    }
}
