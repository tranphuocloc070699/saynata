package com.englishtranslation.backend.admin;

import com.englishtranslation.backend.translation.Explanation;
import com.englishtranslation.backend.translation.SentenceTranslation;
import com.englishtranslation.backend.translation.SentenceTranslationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Admin API for the sentences Claude explained (table sentence_translation). Same shape as AdminWordController. */
@RestController
@RequestMapping("/api/admin/sentences")
public class AdminSentenceController {

    private static final Logger log = LoggerFactory.getLogger(AdminSentenceController.class);

    private static final TypeReference<List<Explanation.Word>> WORD_LIST = new TypeReference<>() {
    };

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "sourceText", "sourceText",
            "targetLang", "targetLang",
            "needsReview", "needsReview",
            "model", "model",
            "promptVersion", "promptVersion",
            "createdAt", "createdAt");

    /** One sentence as the admin web app sees it. explanation has the same shape as in POST /api/translations. */
    public record AdminSentence(Long id, String sourceText, String targetLang, Explanation explanation,
            String model, String promptVersion, boolean needsReview, Instant createdAt) {
    }

    private final SentenceTranslationRepository repository;
    private final JsonMapper jsonMapper;
    private final EntryParser parser;

    public AdminSentenceController(SentenceTranslationRepository repository, JsonMapper jsonMapper,
            EntryParser parser) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.parser = parser;
    }

    @GetMapping
    public ResponseEntity<List<AdminSentence>> list(
            @RequestParam(name = "_start", defaultValue = "0") int start,
            @RequestParam(name = "_end", defaultValue = "10") int end,
            @RequestParam(name = "_sort", defaultValue = "id") String sort,
            @RequestParam(name = "_order", defaultValue = "DESC") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "needs_review", required = false) Boolean needsReview) {
        Page<SentenceTranslation> page = repository.search(
                AdminListParams.likePattern(q),
                needsReview != null,
                needsReview != null && needsReview,
                AdminListParams.pageable(start, end, sort, order, SORT_FIELDS));
        List<AdminSentence> sentences = page.getContent().stream().map(this::toAdminSentence).toList();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(sentences);
    }

    @GetMapping("/{id}")
    public AdminSentence get(@PathVariable long id) {
        return toAdminSentence(find(id));
    }

    /** Body: {"explanation": {"meaning": "...", "words": [...], "translation": "..."}}. Other fields are ignored. */
    @PutMapping("/{id}")
    public AdminSentence update(@PathVariable long id, @RequestBody JsonNode body) {
        SentenceTranslation sentence = find(id);
        Explanation explanation = parser.parseExplanation(body);
        sentence.setExplanation(explanation.meaning(), jsonMapper.writeValueAsString(explanation.words()),
                explanation.translation());
        return toAdminSentence(repository.save(sentence));
    }

    @PostMapping("/{id}/reviewed")
    public AdminSentence markReviewed(@PathVariable long id) {
        SentenceTranslation sentence = find(id);
        sentence.setNeedsReview(false);
        return toAdminSentence(repository.save(sentence));
    }

    /** Deletes the row. The next public request for this sentence asks Claude again. */
    @DeleteMapping("/{id}")
    public AdminSentence delete(@PathVariable long id) {
        SentenceTranslation sentence = find(id);
        repository.delete(sentence);
        return toAdminSentence(sentence);
    }

    private SentenceTranslation find(long id) {
        return repository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No sentence with id " + id + "."));
    }

    private AdminSentence toAdminSentence(SentenceTranslation row) {
        List<Explanation.Word> words = List.of();
        try {
            words = jsonMapper.readValue(row.getWordsJson(), WORD_LIST);
        } catch (RuntimeException e) {
            log.warn("sentence_translation {} has words_json that cannot be read: {}", row.getId(), e.toString());
        }
        Explanation explanation = new Explanation(row.getMeaning(), words, row.getTranslation());
        return new AdminSentence(row.getId(), row.getSourceText(), row.getTargetLang(), explanation,
                row.getModel(), row.getPromptVersion(), row.isNeedsReview(), row.getCreatedAt());
    }
}
