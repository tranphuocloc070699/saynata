package com.englishtranslation.backend.translation;

import java.util.List;

/**
 * What the app shows for one sentence. Same JSON shape as `Explanation` in the iOS app.
 * meaning and words are in simple English; translation is in the requested target language.
 */
public record Explanation(String meaning, List<Word> words, String translation) {

    /** One difficult word or phrase, written as it appears in the sentence. */
    public record Word(String word, String partOfSpeech, String meaning) {
    }
}
