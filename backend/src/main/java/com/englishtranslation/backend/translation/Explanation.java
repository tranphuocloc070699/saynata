package com.englishtranslation.backend.translation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What the app shows for one sentence. Same JSON shape as `Explanation` in the iOS app.
 * Explain first, translate last: meaning and words are in simple English (B1-B2) and come first;
 * translation is a small helper line in the requested target language and comes last.
 */
@JsonPropertyOrder({ "meaning", "words", "translation" })
public record Explanation(String meaning, List<Word> words, String translation) {

    /** One difficult word or phrase, written as it appears in the sentence. */
    public record Word(String word, String partOfSpeech, String meaning) {
    }
}
