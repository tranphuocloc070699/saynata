package com.englishtranslation.backend.translation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What the app shows for one sentence. Same JSON shape as `Explanation` in the iOS app.
 * Explain first, translate last: meaning and words are in simple English (B1-B2) and come first;
 * translation is a small helper line in the requested target language and comes last.
 * {@code better} is the text with its real mistakes fixed and {@code betterWhy} says why in one line (TASK-110);
 * both are null when the text is already natural, or for answers saved before prompt v10.
 */
@JsonPropertyOrder({ "meaning", "words", "better", "betterWhy", "translation" })
public record Explanation(String meaning, List<Word> words, String better, String betterWhy, String translation) {

    /** An explanation without a fix. */
    public Explanation(String meaning, List<Word> words, String translation) {
        this(meaning, words, null, null, translation);
    }

    /** The same explanation without its fix. */
    public Explanation withoutBetter() {
        return new Explanation(meaning, words, translation);
    }

    /** One difficult word or phrase, written as it appears in the sentence. */
    public record Word(String word, String partOfSpeech, String meaning) {
    }
}
