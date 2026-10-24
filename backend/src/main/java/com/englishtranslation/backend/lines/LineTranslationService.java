package com.englishtranslation.backend.lines;

import com.englishtranslation.backend.cache.CacheKeys;
import com.englishtranslation.backend.claude.ClaudeService;
import com.englishtranslation.backend.common.ApiException;
import com.englishtranslation.backend.limits.ClaudeGate;
import com.englishtranslation.backend.translation.TargetLanguage;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Language lines: cache first (table line_translation), then ONE Claude call for all misses of a request.
 * The call goes through {@link ClaudeGate}, so a guest, the rate limit and the daily budget are checked as for
 * every other new Claude call. A cachedOnly request never reaches the gate.
 */
@Service
public class LineTranslationService {

    private static final Logger log = LoggerFactory.getLogger(LineTranslationService.class);

    static final int MAX_TEXTS = 20;
    static final int MAX_TEXT = 500;

    private final LineTranslationRepository lines;
    private final ClaudeService claude;
    private final ClaudeGate gate;
    private final Clock clock;
    private final String promptVersion;

    public LineTranslationService(LineTranslationRepository lines, ClaudeService claude, ClaudeGate gate, Clock clock,
            @Value("${app.claude.line-prompt-version:v1}") String promptVersion) {
        this.lines = lines;
        this.claude = claude;
        this.gate = gate;
        this.clock = clock;
        this.promptVersion = promptVersion;
    }

    public TranslateLinesResponse translate(TranslateLinesRequest request, boolean cachedOnly) {
        return translate(request, cachedOnly, false);
    }

    /**
     * {@code alreadyGated}: this request already passed {@link ClaudeGate#beforeNewCall} for its sentence, so the line
     * call only checks the money budget and does not use up a second allowance of the user's limit.
     */
    private TranslateLinesResponse translate(TranslateLinesRequest request, boolean cachedOnly, boolean alreadyGated) {
        TargetLanguage lang = request.targetLang();
        if (lang == null || lang == TargetLanguage.EN) {
            throw bad("invalid_target_lang", "Choose a target language other than English.");
        }
        List<String> texts = request.texts();
        if (texts == null || texts.isEmpty() || texts.size() > MAX_TEXTS) {
            throw bad("invalid_texts", "Send 1-" + MAX_TEXTS + " texts.");
        }
        List<String> normalized = new ArrayList<>();
        for (String text : texts) {
            String clean = normalize(text);
            if (clean.isEmpty() || clean.length() > MAX_TEXT) {
                throw bad("invalid_texts", "Each text must be 1-" + MAX_TEXT + " characters.");
            }
            normalized.add(clean);
        }
        // hash -> normalized text; the same text twice is asked once.
        Map<String, String> hashToText = new LinkedHashMap<>();
        for (String clean : normalized) {
            hashToText.putIfAbsent(hash(clean), clean);
        }

        String code = lang.code();
        Map<String, String> found = new HashMap<>();
        for (LineTranslation saved : lines.findByTargetLangAndPromptVersionAndTextHashIn(code, promptVersion,
                hashToText.keySet())) {
            found.put(saved.getTextHash(), saved.getTranslation());
        }
        Map<String, String> misses = new LinkedHashMap<>();
        hashToText.forEach((hash, text) -> {
            if (!found.containsKey(hash)) {
                misses.put(hash, text);
            }
        });

        Map<String, String> fresh = new HashMap<>();
        if (!cachedOnly && !misses.isEmpty()) {
            fresh = askClaude(lang, misses, alreadyGated);
        }

        List<TranslateLinesResponse.Line> answer = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String hash = hash(normalized.get(i));
            String saved = found.get(hash);
            answer.add(saved != null
                    ? new TranslateLinesResponse.Line(texts.get(i), saved, true)
                    : new TranslateLinesResponse.Line(texts.get(i), fresh.get(hash), false));
        }
        return new TranslateLinesResponse(lang, answer);
    }

    /**
     * The language line of ONE sentence for the sentence answer (TASK-106.6), or empty when there is none.
     * The text is already normalized. Empty also when the text is too long for a line (over {@value #MAX_TEXT}),
     * when {@code cachedOnly} is set and the line is not saved, and when the call is refused or fails: the English
     * part is already paid and good, so a missing helper line must never cost the learner the whole answer.
     * With {@code store} false (personal text: email, phone, link) the line is neither read from nor saved to the
     * shared table; it is made fresh and thrown away.
     * {@code alreadyGated}: the sentence call of this same request already passed the gate, so only the budget is checked.
     */
    public Optional<String> lineFor(TargetLanguage lang, String normalizedText, boolean cachedOnly, boolean store,
            boolean alreadyGated) {
        if (lang == null || lang == TargetLanguage.EN || normalizedText.isEmpty() || normalizedText.length() > MAX_TEXT) {
            return Optional.empty();
        }
        try {
            if (store) {
                TranslateLinesResponse.Line line = translate(
                        new TranslateLinesRequest(lang, List.of(normalizedText)), cachedOnly, alreadyGated).lines().get(0);
                return Optional.ofNullable(line.translation());
            }
            if (cachedOnly) {
                return Optional.empty();
            }
            checkGate(alreadyGated);
            String line = claude.translateLines(lang, List.of(normalizedText)).get(0).strip();
            return line.isEmpty() ? Optional.empty() : Optional.of(line);
        } catch (RuntimeException e) {
            // A guest, the rate limit and the daily budget end here too (ApiException): the app shows its fallback.
            log.info("No language line for a sentence ({}): {}", lang.code(), e.toString());
            return Optional.empty();
        }
    }

    private void checkGate(boolean alreadyGated) {
        if (alreadyGated) {
            gate.checkBudget();
        } else {
            gate.beforeNewCall();
        }
    }

    /** One call for every miss, then each line is saved. Returns hash -> translation. */
    private Map<String, String> askClaude(TargetLanguage lang, Map<String, String> misses, boolean alreadyGated) {
        checkGate(alreadyGated);
        List<String> missTexts = List.copyOf(misses.values());
        List<String> translated = claude.translateLines(lang, missTexts);
        if (translated.size() != missTexts.size()) {
            throw new IllegalStateException("Claude returned " + translated.size() + " lines for "
                    + missTexts.size() + " texts.");
        }
        Map<String, String> result = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, String> miss : misses.entrySet()) {
            String line = translated.get(i++).strip();
            if (line.isEmpty()) {
                continue; // an empty line is not saved; the app shows its fallback
            }
            result.put(miss.getKey(), line);
            save(lang, miss.getKey(), miss.getValue(), line);
        }
        return result;
    }

    private void save(TargetLanguage lang, String hash, String text, String line) {
        try {
            lines.saveAndFlush(new LineTranslation(hash, text, lang.code(), promptVersion, line, "llm",
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Another request saved the same line at the same moment. Ours is still a good answer.
        }
    }

    /** Trim and collapse inner whitespace to one space. */
    static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    static String hash(String normalizedText) {
        return CacheKeys.key(normalizedText);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
