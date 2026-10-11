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
    private final SimpleWords simpleWords;

    public WordService(WordLookupRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper, EnglishWords englishWords,
            IpaDictionary ipaDictionary, SimpleWords simpleWords) {
        this.simpleWords = simpleWords;
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
        boolean needsReview = false;
        List<String> problems = simpleWords.problems(entry, normalized);
        if (!problems.isEmpty()) {
            // One retry with feedback, never more. If it still fails, we save it and mark it (TASK-16).
            entry = retryWithFeedback(normalized, entry, problems);
            problems = simpleWords.problems(entry, normalized);
            needsReview = !problems.isEmpty();
            if (needsReview) {
                log.warn("'{}' saved with needsReview, still hard after the retry: {}", normalized, problems);
            }
        }
        try {
            repository.saveAndFlush(new WordLookup(
                    cacheKey, normalized, jsonMapper.writeValueAsString(entry), properties.model(),
                    properties.wordPromptVersion(), needsReview));
            return response(normalized, entry, false);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            WordLookup first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return response(normalized, readEntry(first), true);
        }
    }

    /** Asks Claude once more, naming the problems. If that call fails, we keep the first entry. */
    private WordEntry retryWithFeedback(String word, WordEntry first, List<String> problems) {
        String feedback = "Your last answer used words that are too hard. Write the whole entry again. "
                + "Use only very common words in definitions and in easy examples. Problems: "
                + String.join("; ", problems);
        try {
            return claude.describeWordAgain(word, feedback);
        } catch (RuntimeException e) {
            log.warn("Retry for '{}' failed, keeping the first entry: {}", word, e.toString());
            return first;
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
