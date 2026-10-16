package com.englishtranslation.backend.word;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.cache.NotCachedException;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.ClaudeGate;
import com.englishtranslation.backend.limits.PrefillContext;
import com.englishtranslation.backend.dictionary.BundledEntryReader;
import com.englishtranslation.backend.dictionary.DictionaryWord;
import com.englishtranslation.backend.dictionary.DictionaryWordRepository;
import com.englishtranslation.backend.media.BackendWordMediaService;
import com.englishtranslation.backend.media.SenseMedia;
import com.englishtranslation.backend.media.SenseMediaReader;
import java.util.List;
import java.util.Map;
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
    private final BackendWordMediaService mediaService;
    private final SenseMediaReader mediaReader;
    private final DictionaryWordRepository bundled;
    private final BundledEntryReader bundledReader;
    private final ClaudeGate gate;

    public WordService(WordLookupRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper, EnglishWords englishWords,
            IpaDictionary ipaDictionary, SimpleWords simpleWords,
            BackendWordMediaService mediaService, SenseMediaReader mediaReader,
            DictionaryWordRepository bundled, BundledEntryReader bundledReader, ClaudeGate gate) {
        this.gate = gate;
        this.bundled = bundled;
        this.bundledReader = bundledReader;
        this.mediaService = mediaService;
        this.mediaReader = mediaReader;
        this.simpleWords = simpleWords;
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.englishWords = englishWords;
        this.ipaDictionary = ipaDictionary;
    }

    public WordResponse lookUp(String word) {
        return lookUp(word, false).response();
    }

    /**
     * With {@code cachedOnly} (TASK-83.4) only the bundled and cached answers are given; a miss throws
     * {@link NotCachedException} before ClaudeGate, so the app can ask while the user types.
     */
    public WordAnswer lookUp(String word, boolean cachedOnly) {
        String normalized = CacheKeys.normalizeWord(word);

        // A bundled word is answered from its own entry. The bundled entry wins (TASK-58): Claude is not
        // called and no word_lookup row is made, so a word is never in both lists.
        Optional<DictionaryWord> bundledWord = bundled.findWithSensesByLemma(normalized);
        if (bundledWord.isPresent()) {
            return new WordAnswer(bundledResponse(normalized, bundledWord.get()), WordAnswer.Origin.BUNDLED);
        }

        // Not a real word: answer "no senses" (the app shows "not an English word") without calling Claude.
        // Nothing is saved, so a later update of the word list can fix a missing word.
        if (!englishWords.contains(normalized)) {
            log.debug("'{}' is not on the English word list, Claude not called", normalized);
            return new WordAnswer(new WordResponse(normalized, new WordEntryView(List.of()), false, null),
                    WordAnswer.Origin.NOT_A_WORD);
        }

        String cacheKey = CacheKeys.key("word", properties.wordPromptVersion(), normalized);

        Optional<WordLookup> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return new WordAnswer(response(normalized, readEntry(saved.get()), true, mediaOf(saved.get().getId())),
                    WordAnswer.Origin.CACHED);
        }
        if (cachedOnly) {
            throw new NotCachedException();
        }

        // Only a word that is not bundled and not cached gets here. A guest cannot cause a Claude call.
        gate.beforeNewCall();
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
            WordLookup row = new WordLookup(
                    cacheKey, normalized, jsonMapper.writeValueAsString(entry), properties.model(),
                    properties.wordPromptVersion(), needsReview);
            row.setCefr(entry.firstCefr());
            repository.saveAndFlush(row);
            startMediaJob(row.getId(), normalized, entry);
            return new WordAnswer(response(normalized, entry, false, Map.of()), WordAnswer.Origin.NEW);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            WordLookup first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return new WordAnswer(response(normalized, readEntry(first), true, mediaOf(first.getId())),
                    WordAnswer.Origin.CACHED);
        }
    }

    /** The reply for a bundled word: its entry and media from Postgres, cached = true (nothing was generated). */
    private WordResponse bundledResponse(String word, DictionaryWord bundledWord) {
        WordEntry entry = bundledReader.read(bundledWord);
        Map<Integer, SenseMedia> media;
        try {
            media = mediaReader.forBundledWords(List.of(bundledWord.getId())).getOrDefault(bundledWord.getId(), Map.of());
        } catch (RuntimeException e) {
            log.warn("Could not read the media of dictionary_word {}: {}", bundledWord.getId(), e.toString());
            media = Map.of();
        }
        String ipa = bundledWord.getIpa() != null ? bundledWord.getIpa() : ipaDictionary.ipaFor(word);
        return new WordResponse(word, WordEntryView.of(entry, media), true, ipa);
    }

    /** Asks Claude once more, naming the problems. If that call fails, we keep the first entry. */
    private WordEntry retryWithFeedback(String word, WordEntry first, List<String> problems) {
        String feedback = "Your last answer used words that are too hard. Write the whole entry again. "
                + "Use only very common words in definitions and in easy examples. Problems: "
                + String.join("; ", problems);
        try {
            gate.checkBudget(); // the second call of the same request: only the money limit is checked again
            return claude.describeWordAgain(word, feedback);
        } catch (RuntimeException e) {
            log.warn("Retry for '{}' failed, keeping the first entry: {}", word, e.toString());
            return first;
        }
    }

    /** Adds the IPA at request time, so rows saved before IPA existed get it too. */
    private WordResponse response(String word, WordEntry entry, boolean cached, Map<Integer, SenseMedia> media) {
        return new WordResponse(word, WordEntryView.of(entry, media), cached, ipaDictionary.ipaFor(word));
    }

    /** The media of a saved word, by sense position. A media problem must never break the word answer. */
    private Map<Integer, SenseMedia> mediaOf(Long lookupId) {
        try {
            return mediaReader.forBackendWords(List.of(lookupId)).getOrDefault(lookupId, Map.of());
        } catch (RuntimeException e) {
            log.warn("Could not read the media of word_lookup {}: {}", lookupId, e.toString());
            return Map.of();
        }
    }

    /**
     * Starts the picture job on its own thread. It is not waited for; the picture shows on the next lookup.
     * A word saved by the prefill job gets no picture (developer, 10-16): no Pixabay call, no claim row, no ffmpeg.
     */
    private void startMediaJob(Long lookupId, String word, WordEntry entry) {
        if (PrefillContext.current().isPresent()) {
            log.debug("'{}' was saved by the prefill job, no picture", word);
            return;
        }
        try {
            mediaService.fetchInBackground(lookupId, word, entry);
        } catch (RuntimeException e) {
            log.warn("Could not start the media job for '{}': {}", word, e.toString());
        }
    }

    private WordEntry readEntry(WordLookup lookup) {
        return jsonMapper.readValue(lookup.getEntryJson(), WordEntry.class);
    }
}
