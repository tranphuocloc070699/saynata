package com.englishtranslation.backend.translation;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.cache.NotCachedException;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.ClaudeGate;
import com.englishtranslation.backend.lines.LineTranslationService;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache first: read the DB, and only call Claude on a miss.
 *
 * A sentence answer has two parts (TASK-106.6). The English part (prompt v10) is cached once for all languages: its
 * key has no language. The language line {@code explanation.translation} comes from {@code line_translation}
 * through {@link LineTranslationService}: saved line, else one small line call, else "" (never null: old apps decode a String; a guest, a cachedOnly
 * request, a refused or failed call). For English there is no line call; the translation is the text itself.
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
    private final PersonalTextDetector personalTextDetector;
    private final ClaudeGate gate;
    private final LineTranslationService lines;

    public TranslationService(SentenceTranslationRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper, PersonalTextDetector personalTextDetector, ClaudeGate gate,
            LineTranslationService lines) {
        this.gate = gate;
        this.lines = lines;
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.personalTextDetector = personalTextDetector;
    }

    public TranslationResponse translate(String text, TargetLanguage targetLang) {
        return translate(text, targetLang, false);
    }

    /** With {@code cachedOnly} (TASK-83.4) a miss throws {@link NotCachedException} and Claude is never asked. */
    public TranslationResponse translate(String text, TargetLanguage targetLang, boolean cachedOnly) {
        return translate(text, targetLang, cachedOnly, normalized -> claude.explainSentence(normalized));
    }

    /** Same as the four-argument version, for callers that do not want the word-by-word text. */
    public TranslationResponse translateStreaming(String text, TargetLanguage targetLang, Consumer<String> onMeaning) {
        return translateStreaming(text, targetLang, delta -> {
        }, onMeaning);
    }

    /**
     * Same as {@link #translate}, but a Claude call is streamed. While Claude writes the simple English meaning,
     * {@code onMeaningDelta} gets its new characters (about every 50 ms). {@code onMeaning} gets the whole meaning
     * once it is complete, before the words and the translation are done.
     * On a cache hit neither is called. The return value is the same as {@link #translate} gives.
     */
    public TranslationResponse translateStreaming(String text, TargetLanguage targetLang,
            Consumer<String> onMeaningDelta, Consumer<String> onMeaning) {
        return translateStreaming(text, targetLang, false, onMeaningDelta, onMeaning);
    }

    /** The streaming version with {@code cachedOnly}: a miss throws {@link NotCachedException}. */
    public TranslationResponse translateStreaming(String text, TargetLanguage targetLang, boolean cachedOnly,
            Consumer<String> onMeaningDelta, Consumer<String> onMeaning) {
        return translate(text, targetLang, cachedOnly, normalized -> {
            MeaningStream meaningStream = new MeaningStream(onMeaningDelta, onMeaning);
            return claude.explainSentenceStreaming(normalized, meaningStream::feed);
        });
    }

    /** The cache rules, shared by both ways in. {@code askClaude} runs only when there is no cached answer. */
    private TranslationResponse translate(String text, TargetLanguage targetLang, boolean cachedOnly,
            Function<String, Explanation> callClaude) {
        // Every way to Claude goes through the gate: signed in? budget left? under the user's limit?
        // It runs only here, after the cache lookup failed, so cache hits stay free for everyone.
        // A cachedOnly request stops before the gate: personal text (never cached) and misses get not_cached.
        Function<String, Explanation> askClaude = normalizedText -> {
            if (cachedOnly) {
                throw new NotCachedException();
            }
            gate.beforeNewCall();
            return BetterCheck.apply(normalizedText, callClaude.apply(normalizedText));
        };
        String normalized = CacheKeys.normalizeSentence(text);

        // Personal text (email, phone, link, long number) is explained, but never saved to
        // or read from the shared cache. Its language line is not saved either.
        if (personalTextDetector.looksPersonal(normalized)) {
            Explanation explanation = askClaude.apply(normalized);
            return new TranslationResponse(normalized, targetLang,
                    withLanguageLine(explanation, targetLang, normalized, cachedOnly, false, true), false);
        }

        String version = properties.sentencePromptVersion();
        String cacheKey = CacheKeys.sentenceKey(version, normalized);

        Optional<SentenceTranslation> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return answer(normalized, targetLang, readExplanation(saved.get()), true, cachedOnly);
        }

        // Exact text missed. Try the loose key: "how are you" finds a saved "How are you?".
        // The exact key was tried first, so "US" and "us" stay apart when both are saved.
        if (!CacheKeys.normalizeSentenceLoose(normalized).isEmpty()) {
            String matchKey = CacheKeys.sentenceMatchKey(version, normalized);
            Optional<SentenceTranslation> similar = repository.findFirstByMatchKeyOrderByCreatedAtAscIdAsc(matchKey);
            if (similar.isPresent()) {
                // The text in the answer is what the user typed; the explanation is the saved one.
                return answer(normalized, targetLang, readExplanation(similar.get()), true, cachedOnly);
            }
        }

        Explanation explanation = askClaude.apply(normalized);
        try {
            // The row has no language: "en" is the language of the explanation, and the translation column stays empty.
            repository.saveAndFlush(new SentenceTranslation(
                    cacheKey, normalized, "en", explanation.meaning(),
                    jsonMapper.writeValueAsString(explanation.words()), explanation.better(),
                    explanation.betterWhy(), "", properties.model(), version));
            return answer(normalized, targetLang, explanation, false, cachedOnly);
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same key while we waited for Claude. Keep the first answer.
            SentenceTranslation first = repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
            return answer(normalized, targetLang, readExplanation(first), true, cachedOnly);
        }
    }

    /** The English part is ready (cached or new): add the language line. */
    private TranslationResponse answer(String normalized, TargetLanguage targetLang, Explanation english, boolean cached,
            boolean cachedOnly) {
        // A new English part means this request already passed the gate; the line call then skips the user limit.
        return new TranslationResponse(normalized, targetLang,
                withLanguageLine(english, targetLang, normalized, cachedOnly, true, !cached), cached);
    }

    private Explanation withLanguageLine(Explanation english, TargetLanguage targetLang, String normalized,
            boolean cachedOnly, boolean store, boolean alreadyGated) {
        String line = targetLang == TargetLanguage.EN
                ? normalized
                : lines.lineFor(targetLang, normalized, cachedOnly, store, alreadyGated).orElse("");
        return new Explanation(english.meaning(), english.words(), english.better(), english.betterWhy(), line);
    }

    private Explanation readExplanation(SentenceTranslation row) {
        List<Explanation.Word> words = jsonMapper.readValue(row.getWordsJson(), WORD_LIST);
        return new Explanation(row.getMeaning(), words, row.getBetter(), row.getBetterWhy(), "");
    }
}
