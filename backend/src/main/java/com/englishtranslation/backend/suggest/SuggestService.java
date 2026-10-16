package com.englishtranslation.backend.suggest;

import com.englishtranslation.backend.claude.ClaudeProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Prefix suggestions while the user types (TASK-83.4): bundled words, cached backend words and cached phrases.
 * Only text that already has an answer is suggested, so tapping one is a cache hit, never a Claude call.
 *
 * Each source is one LIMITed query on a prefix index (V19), so the whole call stays far under 50 ms. The cached
 * words and phrases are not sorted in SQL on purpose: "ORDER BY LENGTH" would read every match first (89 ms for
 * 200,000 phrases on "p"), while a plain LIMIT stops after a few rows (under 1 ms). Java sorts the few rows.
 */
@Service
public class SuggestService {

    static final int MAX_SUGGESTIONS = 8;
    /** When there are phrases, words keep at most this many places, so a phrase still shows. */
    static final int MAX_WORDS_WITH_PHRASES = 5;
    static final int MAX_PREFIX_LENGTH = 100;
    /** Rows read from the cached words and the cached phrases, before sorting and removing duplicates. */
    static final int ROWS_PER_SOURCE = 24;

    private final JdbcClient jdbc;
    private final ClaudeProperties properties;

    public SuggestService(JdbcClient jdbc, ClaudeProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /**
     * Words first (an exact match first, then bundled words by NGSL rank, then cached words, shorter first),
     * then phrases, shorter first. {@code lang} limits phrases to those cached for that language; null = any.
     */
    public SuggestResponse suggest(String rawPrefix, String lang) {
        String prefix = normalize(rawPrefix);
        if (prefix.isEmpty()) {
            return new SuggestResponse(prefix, List.of());
        }
        String like = escapeLike(prefix) + "%";

        Set<String> words = new LinkedHashSet<>();
        words.addAll(bundledWords(like));
        words.addAll(cachedWords(like));
        if (words.remove(prefix)) {
            List<String> withExactFirst = new ArrayList<>(List.of(prefix));
            withExactFirst.addAll(words);
            words = new LinkedHashSet<>(withExactFirst);
        }

        // Several languages can save the same text: keep one phrase per lower-case text, and none that is a word.
        Set<String> seen = new HashSet<>(words);
        List<String> phrases = new ArrayList<>();
        for (String phrase : cachedPhrases(like, lang)) {
            if (seen.add(phrase.toLowerCase(Locale.ROOT))) {
                phrases.add(phrase);
            }
        }

        int wordPlaces = phrases.isEmpty() ? MAX_SUGGESTIONS
                : Math.max(MAX_WORDS_WITH_PHRASES, MAX_SUGGESTIONS - phrases.size());
        List<Suggestion> suggestions = new ArrayList<>();
        words.stream().limit(wordPlaces).forEach(word -> suggestions.add(Suggestion.word(word)));
        phrases.stream().limit(MAX_SUGGESTIONS - suggestions.size())
                .forEach(phrase -> suggestions.add(Suggestion.phrase(phrase)));
        return new SuggestResponse(prefix, suggestions);
    }

    private List<String> bundledWords(String like) {
        return jdbc.sql("""
                SELECT lemma FROM dictionary_word
                WHERE lemma LIKE :like ESCAPE '\\'
                ORDER BY word_rank, lemma
                LIMIT :limit""")
                .param("like", like)
                .param("limit", MAX_SUGGESTIONS)
                .query(String.class).list();
    }

    /** Only rows of the current prompt version: older rows are not cache hits any more. Shorter first. */
    private List<String> cachedWords(String like) {
        return shortestFirst(jdbc.sql("""
                SELECT word FROM word_lookup
                WHERE word LIKE :like ESCAPE '\\' AND prompt_version = :version
                LIMIT :limit""")
                .param("like", like)
                .param("version", properties.wordPromptVersion())
                .param("limit", ROWS_PER_SOURCE)
                .query(String.class).list());
    }

    /** Shorter first. The same text in two languages is one suggestion, so more rows than needed are read. */
    private List<String> cachedPhrases(String like, String lang) {
        return shortestFirst(jdbc.sql("""
                SELECT source_text FROM sentence_translation
                WHERE LOWER(source_text) LIKE :like ESCAPE '\\' AND prompt_version = :version
                  AND (CAST(:lang AS VARCHAR(8)) IS NULL OR target_lang = :lang)
                LIMIT :limit""")
                .param("like", like)
                .param("version", properties.sentencePromptVersion())
                .param("lang", lang)
                .param("limit", ROWS_PER_SOURCE)
                .query(String.class).list());
    }

    private static List<String> shortestFirst(List<String> texts) {
        return texts.stream().sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Lower case, spaces trimmed and squeezed, cut to the column-safe length. */
    static String normalize(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_PREFIX_LENGTH ? normalized.substring(0, MAX_PREFIX_LENGTH) : normalized;
    }

    /** "50%" must find "50%..." and not every text starting with "50". */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
