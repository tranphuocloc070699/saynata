package com.englishtranslation.backend.claude;

import com.englishtranslation.backend.translation.Explanation;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What Claude returns for a sentence (prompt v9): a flag, a short summary in simple English (one line, or a
 * gist line and up to 3 "- " bullets with **bold** key words), and the translation. The flag is only for the backend: ClaudeService turns "false" into a {@link NotEnglishException},
 * so the app and the DB never see it. There is no words list any more; the app explains words from the dictionary.
 */
@JsonPropertyOrder({ "isEnglish", "summary", "translation" })
public record SentenceAnswer(
        @JsonPropertyDescription("true only if the text is English. false for code, gibberish or another language.") boolean isEnglish,
        @JsonPropertyDescription("What the text says in simple B1 English. Short text: one sentence. Longer text: a gist line, then up to 3 lines starting with \"- \", at most half as long; **bold** for 1-3 key terms. The content itself, never a description of it.") String summary,
        String translation) {

    /**
     * The explanation to show. Call this only after checking {@link #isEnglish()}. Old app versions still read
     * {@code meaning} and {@code words}, so the summary is the meaning and the words list is always empty.
     * The summary is cleaned to the Markdown subset the clients render ({@link SummaryFormat}).
     */
    public Explanation toExplanation() {
        return new Explanation(SummaryFormat.clean(summary), List.of(), translation);
    }
}
