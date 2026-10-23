package com.englishtranslation.backend.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Turns user input into a stable cache key. The iOS app must normalise the same way,
 * or the phone and the server will store the same text under different keys.
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** Trim and collapse inner whitespace. Case is kept: "US" and "us" are different. */
    public static String normalizeSentence(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    /**
     * The loose form of a sentence, for the second lookup when the exact key misses (TASK-83.3): normalised, lowercase,
     * curly quotes and apostrophes made straight, every kind of space (also the no-break space) collapsed to one, and
     * without . ? ! … , (and spaces) at the end, and without one pair of quotes around the whole text (TASK-83.13). "How are you?" and "how are you" give the same text, and so do
     * "I’m fine…" and "i'm fine". The "?" is ignored too: "Is it OK" is typed as the same question.
     * It can be empty (the input was only "?!"); then there is nothing to match.
     */
    public static String normalizeSentenceLoose(String text) {
        String lower = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\u2018\u2019\u201A\u201B\u02BC\u2032]", "'")
                .replaceAll("[\u201C\u201D\u201E\u201F\u2033]", "\"")
                .replaceAll("[\\s\\p{Z}]+", " ")
                .strip();
        lower = withoutWrappingQuotes(lower);
        int end = lower.length();
        while (end > 0 && isLooseEnd(lower.charAt(end - 1))) {
            end--;
        }
        return lower.substring(0, end);
    }

    /**
     * TASK-83.13: removes ONE pair of " or ' that wraps the whole text, also when the final . ? ! … sits outside the
     * closing quote: "Looks good." and "Looks good". both give Looks good. A pair at both ends only, so 'cause I said so
     * is kept. If the same quote appears inside the text ("a" and "b"), these are two quotes, not one wrapper, and
     * nothing is removed. An apostrophe between two letters (don't) does not count as a quote.
     */
    private static String withoutWrappingQuotes(String text) {
        if (text.length() < 2) {
            return text;
        }
        char quote = text.charAt(0);
        if (quote != '"' && quote != '\'') {
            return text;
        }
        int end = text.length();
        while (end > 1 && isLooseEnd(text.charAt(end - 1))) {
            end--;
        }
        if (end < 2 || text.charAt(end - 1) != quote) {
            return text;
        }
        String inner = text.substring(1, end - 1);
        for (int i = 0; i < inner.length(); i++) {
            if (inner.charAt(i) == quote && !isBetweenLetters(inner, i)) {
                return text;
            }
        }
        return inner;
    }

    private static boolean isBetweenLetters(String text, int i) {
        return i > 0 && i < text.length() - 1
                && Character.isLetter(text.charAt(i - 1)) && Character.isLetter(text.charAt(i + 1));
    }

    private static boolean isLooseEnd(char c) {
        return c == '.' || c == '?' || c == '!' || c == '\u2026' || c == ',' || c == ' ';
    }

    /**
     * The match_key of a saved sentence: key("sentence-loose", promptVersion, targetLang, looseText).
     * Flyway V18 filled old rows with this method, and V24 filled them again when the loose form grew (curly quotes,
     * …, commas), so the database and new code always agree.
     */
    public static String sentenceMatchKey(String promptVersion, String targetLang, String text) {
        return key("sentence-loose", promptVersion, targetLang, normalizeSentenceLoose(text));
    }

    /** Same as a sentence, plus lowercase: "Run" and "run" are the same word. */
    public static String normalizeWord(String word) {
        return normalizeSentence(word).toLowerCase(Locale.ROOT);
    }

    /**
     * sha256 of the parts joined with "|", as 64 hex characters.
     * Sentence: key("sentence", promptVersion, targetLang, text). Word: key("word", promptVersion, word).
     */
    public static String key(String... parts) {
        String raw = String.join("|", parts);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
