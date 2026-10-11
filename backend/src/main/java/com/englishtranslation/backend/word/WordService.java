package com.englishtranslation.backend.word;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Same cache-first flow as TranslationService, for words missing from the bundled dictionary. */
@Service
public class WordService {

    private static final Logger log = LoggerFactory.getLogger(WordService.class);

    private final WordLookupRepository repository;
    private final ClaudeService claude;
    private final ClaudeProperties properties;
    private final JsonMapper jsonMapper;

    private final EnglishWords englishWords;
    private final IpaDictionary ipaDictionary;

    public WordService(WordLookupRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper, EnglishWords englishWords,
            IpaDictionary ipaDictionary) {
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.englishWords = englishWords;
        this.ipaDictionary = ipaDictionary;
    }

    public WordResponse lookUp(String word) {
        String normalized = CacheKeys.normalizeWord(word);

        // Not a real word: answer "no senses" (the app shows "not an English word") without calling Claude.
        // Nothing is saved, so a later update of the word list can fix a missing word.
        if (!englishWords.contains(normalized)) {
            log.debug("'{}' is not on the English word list, Claude not called", normalized);
            return new WordResponse(normalized, new WordEntry(List.of()), false, null);
        }

        String cacheKey = CacheKeys.key("word", properties.wordPromptVersion(), normalized);

        Optional<WordLookup> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return response(normalized, readEntry(saved.get()), true);
        }

        WordEntry entry = claude.describeWord(normalized);
        try {
            repository.saveAndFlush(new WordLookup(
                    cacheKey, normalized, jsonMapper.writeValueAsString(entry), properties.model(), properties.wordPromptVersion()));
            return response(normalized, entry, false);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            WordLookup first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return response(normalized, readEntry(first), true);
        }
    }

    /** Adds the IPA at request time, so rows saved before IPA existed get it too. */
    private WordResponse response(String word, WordEntry entry, boolean cached) {
        return new WordResponse(word, entry, cached, ipaDictionary.ipaFor(word));
    }

    private WordEntry readEntry(WordLookup lookup) {
        return jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
    }
}
