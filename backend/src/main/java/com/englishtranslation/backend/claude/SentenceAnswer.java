package com.englishtranslation.backend.claude;

import com.englishtranslation.backend.translation.Explanation;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What Claude returns for a sentence: an {@link Explanation} plus a flag. The flag is only for the backend:
 * ClaudeService turns "false" into a {@link NotEnglishException}, so the app and the DB never see it.
 */
@JsonPropertyOrder({ "isEnglish", "meaning", "words", "translation" })
public record SentenceAnswer(
        @JsonPropertyDescription("true only if the text is English. false for code, gibberish or another language.") boolean isEnglish,
        String meaning,
        List<Explanation.Word> words,
        String translation) {

    /** The explanation to show. Call this only after checking {@link #isEnglish()}. */
    public Explanation toExplanation() {
        return new Explanation(meaning, words, translation);
    }
}
