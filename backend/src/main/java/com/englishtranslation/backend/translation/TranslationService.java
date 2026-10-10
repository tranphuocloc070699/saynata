package com.englishtranslation.backend.translation;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache first: read the DB, and only call Claude on a miss.
 *
 * There is no @Transactional here on purpose: the Claude call can take seconds, and a
 * transaction would hold a DB connection open the whole time.
 */
@Service
public class TranslationService {

    private static final TypeReference<List<Explanation.Word>> WORD_LIST = new TypeReference<>() {
    };

    private final SentenceTranslationRepository repository;
    private final ClaudeService claude;
    private final ClaudeProperties properties;
    private final JsonMapper jsonMapper;

    public TranslationService(SentenceTranslationRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper) {
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public TranslationResponse translate(String text, TargetLanguage targetLang) {
        String normalized = CacheKeys.normalizeSentence(text);
        String cacheKey = CacheKeys.key("sentence", properties.sentencePromptVersion(), targetLang.code(), normalized);

        Optional<SentenceTranslation> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return new TranslationResponse(normalized, targetLang, readExplanation(saved.get()), true);
        }

        Explanation explanation = claude.explainSentence(normalized, targetLang);
        try {
            repository.saveAndFlush(new SentenceTranslation(
                    cacheKey, normalized, targetLang.code(), explanation.meaning(),
                    jsonMapper.writeValueAsString(explanation.words()), explanation.translation(),
                    properties.model(), properties.sentencePromptVersion()));
            return new TranslationResponse(normalized, targetLang, explanation, false);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            SentenceTranslation first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return new TranslationResponse(normalized, targetLang, readExplanation(first), true);
        }
    }

    private Explanation readExplanation(SentenceTranslation row) {
        List<Explanation.Word> words = jsonMapper.readValue(row.getWordsJson(), WORD_LIST);
        return new Explanation(row.getMeaning(), words, row.getTranslation());
    }
}
