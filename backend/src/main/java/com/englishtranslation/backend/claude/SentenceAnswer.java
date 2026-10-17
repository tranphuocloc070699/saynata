package com.englishtranslation.backend.claude;

import com.englishtranslation.backend.translation.Explanation;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What Claude returns for a sentence (prompt v8): a flag, the sentence rewritten in simple English, and the
 * translation. The flag is only for the backend: ClaudeService turns "false" into a {@link NotEnglishException},
 * so the app and the DB never see it. There is no words list any more; the app explains words from the dictionary.
 */
@JsonPropertyOrder({ "isEnglish", "summary", "translation" })
public record SentenceAnswer(
        @JsonPropertyDescription("true only if the text is English. false for code, gibberish or another language.") boolean isEnglish,
        @JsonPropertyDescription("The sentence written again in simple B1 English, about the same length. The content itself, never a description of it.") String summary,
        String translation) {

    /**
     * The explanation to show. Call this only after checking {@link #isEnglish()}. Old app versions still read
     * {@code meaning} and {@code words}, so the summary is the meaning and the words list is always empty.
     */
    public Explanation toExplanation() {
        return new Explanation(summary, List.of(), translation);
    }
}
