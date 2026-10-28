package com.saynata.backend.word;

import com.saynata.backend.common.ApiException;
import com.saynata.backend.common.SingleFlight;
import com.saynata.backend.llm.LlmClient;
import com.saynata.backend.llm.LlmGate;
import com.saynata.backend.llm.WordDraft;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Cache-first lookup: the database is the cache of LLM answers.
 *   1. normalize the key      2. database hit -> return it
 *   3. miss -> single-flight  4. the gate (budget, rate limit), then ask the LLM once, check the answer, save it
 *   5. unique index = last guard (a second instance may save the same word first)
 *
 * No @Transactional here on purpose: a slow LLM call must not hold a database connection.
 * Failed or unusable answers are never saved.
 */
@Service
public class WordLookupService {

    private static final Logger log = LoggerFactory.getLogger(WordLookupService.class);

    /** Cheap junk check before we pay: letters, with apostrophe, hyphen and spaces inside (don't, well-known, give up). */
    private static final Pattern LOOKS_LIKE_WORDS = Pattern.compile("^\\p{L}[\\p{L}'’ -]*$");

    private final WordRepository words;
    private final LlmClient llm;
    private final LlmGate gate;
    private final SingleFlight<LookupResponse> flights = new SingleFlight<>(Duration.ofSeconds(30));

    public WordLookupService(WordRepository words, LlmClient llm, LlmGate gate) {
        this.words = words;
        this.llm = llm;
        this.gate = gate;
    }

    /** ip = who is asking. Only the single-flight winner's ip is checked and charged. */
    public LookupResponse lookup(String query, String ip) {
        String key = normalize(query);
        Optional<Word> cached = words.findByHeadwordIgnoreCase(key);
        if (cached.isPresent()) {
            return new LookupResponse(WordResponse.from(cached.get()), "cache");
        }
        return flights.run(key, () -> generate(key, ip));
    }

    private LookupResponse generate(String key, String ip) {
        // Check again: another flight may have saved this word just before we became the winner.
        Optional<Word> again = words.findByHeadwordIgnoreCase(key);
        if (again.isPresent()) {
            return new LookupResponse(WordResponse.from(again.get()), "cache");
        }

        gate.beforeCall(ip); // throws 503 / 429 before we pay anything
        WordDraft draft = callLlm(key);
        gate.afterSuccess(ip, key); // only a good answer is counted
        try {
            Word saved = words.save(new Word(key, draft.partOfSpeech(), draft.ipa(), draft.simpleExplanation().trim()));
            return new LookupResponse(WordResponse.from(saved), "llm");
        } catch (DataIntegrityViolationException e) {
            // Another instance saved the same word first. Return its row, do not fail.
            return words.findByHeadwordIgnoreCase(key)
                    .map(w -> new LookupResponse(WordResponse.from(w), "cache"))
                    .orElseThrow(() -> e);
        }
    }

    private WordDraft callLlm(String key) {
        WordDraft draft;
        try {
            draft = llm.explain(key);
        } catch (RuntimeException e) {
            log.warn("LLM failed for '{}'", key, e);
            throw badAnswer("llm_failed");
        }
        if (!isUsable(draft)) {
            log.warn("LLM gave an unusable answer for '{}'", key);
            throw badAnswer("llm_bad_answer");
        }
        return draft;
    }

    /** Same limits as the columns in V1__create_word.sql. */
    private static boolean isUsable(WordDraft d) {
        return d != null
                && d.simpleExplanation() != null && !d.simpleExplanation().isBlank()
                && (d.partOfSpeech() == null || d.partOfSpeech().length() <= 30)
                && (d.ipa() == null || d.ipa().length() <= 100);
    }

    private static ApiException badAnswer(String code) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, "Could not explain this word right now. Please try again.");
    }

    /** "  Run  " and "RUN" are the same key: trim, lower case, one space between words. */
    static String normalize(String query) {
        String key = query == null ? "" : query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_query", "Type a word or phrase (1 to 100 characters).");
        }
        if (!LOOKS_LIKE_WORDS.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "not_a_word", "Use letters only, for example: serendipity or give up.");
        }
        return key;
    }
}
