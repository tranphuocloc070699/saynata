package com.englishtranslation.backend.translation;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeProperties;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.limits.ClaudeGate;
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

    public TranslationService(SentenceTranslationRepository repository, ClaudeService claude, ClaudeProperties properties,
            JsonMapper jsonMapper, PersonalTextDetector personalTextDetector, ClaudeGate gate) {
        this.gate = gate;
        this.repository = repository;
        this.claude = claude;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.personalTextDetector = personalTextDetector;
    }

    public TranslationResponse translate(String text, TargetLanguage targetLang) {
        return translate(text, targetLang, normalized -> claude.explainSentence(normalized, targetLang));
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
        return translate(text, targetLang, normalized -> {
            MeaningStream meaningStream = new MeaningStream(onMeaningDelta, onMeaning);
            return claude.explainSentenceStreaming(normalized, targetLang, meaningStream::feed);
        });
    }

    /** The cache rules, shared by both ways in. {@code askClaude} runs only when there is no cached answer. */
    private TranslationResponse translate(String text, TargetLanguage targetLang,
            Function<String, Explanation> callClaude) {
        // Every way to Claude goes through the gate: signed in? budget left? under the user's limit?
        // It runs only here, after the cache lookup failed, so cache hits stay free for everyone.
        Function<String, Explanation> askClaude = normalizedText -> {
            gate.beforeNewCall();
            return callClaude.apply(normalizedText);
        };
        String normalized = CacheKeys.normalizeSentence(text);

        // Personal text (email, phone, link, long number) is explained, but never saved to
        // or read from the shared cache.
        if (personalTextDetector.looksPersonal(normalized)) {
            Explanation explanation = askClaude.apply(normalized);
            return new TranslationResponse(normalized, targetLang, explanation, false);
        }

        String cacheKey = CacheKeys.key("sentence", properties.sentencePromptVersion(), targetLang.code(), normalized);

        Optional<SentenceTranslation> saved = repository.findByCacheKey(cacheKey);
        if (saved.isPresent()) {
            return new TranslationResponse(normalized, targetLang, readExplanation(saved.get()), true);
        }

        Explanation explanation = askClaude.apply(normalized);
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
