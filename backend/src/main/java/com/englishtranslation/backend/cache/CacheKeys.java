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
