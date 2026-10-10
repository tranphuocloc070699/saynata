package com.englishtranslation.backend.word;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Same cache-first flow as TranslationService, for words missing from the bundled dictionary. */
@Service
public class WordService {

    private final WordLookupRepository repository;
    private final ClaudeService claude;
    private final ClaudeProperties properties;
    private final JsonMapper jsonMapper;

    public WordService(WordLookupRepository repository, ClaudeService claude, ClaudeProperties properties, JsonMapper jsonMapper) {
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public WordResponse lookUp(String word) {
        String normalized = CacheKeys.normalizeWord(word);
        String cacheKey = CacheKeys.key("word", properties.promptVersion(), normalized);

        Optional<WordLookup> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return new WordResponse(normalized, readEntry(saved.get()), true);
        }

        WordEntry entry = claude.describeWord(normalized);
        try {
            repository.saveAndFlush(new WordLookup(
                    cacheKey, normalized, jsonMapper.writeValueAsString(entry), properties.model(), properties.promptVersion()));
            return new WordResponse(normalized, entry, false);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            WordLookup first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return new WordResponse(normalized, readEntry(first), true);
        }
    }

    private WordEntry readEntry(WordLookup lookup) {
        return jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
    }
}
