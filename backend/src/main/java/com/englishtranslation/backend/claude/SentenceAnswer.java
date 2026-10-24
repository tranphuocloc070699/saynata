package com.englishtranslation.backend.claude;

import com.englishtranslation.backend.translation.Explanation;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * What Claude returns for a sentence (prompt v11): a flag, a short summary in simple English (one line, or a
 * gist line and up to 3 "- " bullets with **bold** key words), and a fixed version of the text when it has a real
 * mistake ("" when it is natural, TASK-110) with a one-line reason. The language line is not asked here (TASK-106.6):
 * it comes from line_translation. The flag is only for the backend: ClaudeService turns "false" into a
 * {@link NotEnglishException}, so the app and the DB never see it. There is no words list any more; the app explains
 * words from the dictionary.
 */
@JsonPropertyOrder({ "isEnglish", "summary", "better", "betterWhy" })
public record SentenceAnswer(
        @JsonPropertyDescription("true only if the text is English. false for code, gibberish or another language.") boolean isEnglish,
        @JsonPropertyDescription("What the text says in simple B1 English. Short text: one sentence. Longer text: a gist line, then up to 3 lines starting with \"- \", at most half as long; **bold** for 1-3 key terms. The content itself, never a description of it.") String summary,
        @JsonPropertyDescription("The text with only its real mistakes fixed (grammar, a wrong word, a phrase a native speaker would not say). \"\" when the text is correct, long, or reads like a book, news or a website.") String better,
        @JsonPropertyDescription("\"\" when better is \"\". Else one short line in simple B1 English that says why.") String betterWhy) {

    /** An answer without a fix, as v10 wrote it. Kept for tests and callers that do not care. */
    public SentenceAnswer(boolean isEnglish, String summary) {
        this(isEnglish, summary, "", "");
    }

    /**
     * The English explanation. The translation is empty here; TranslationService fills it. Call this only after
     * checking {@link #isEnglish()}. Old app versions still read {@code meaning} and {@code words}, so the summary is
     * the meaning and the words list is always empty. The summary is cleaned to the Markdown subset the clients render
     * ({@link SummaryFormat}). A blank fix becomes null; TranslationService still checks a fix against the text
     * ({@code BetterCheck}).
     */
    public Explanation toExplanation() {
        String fix = blankToNull(better);
        return new Explanation(SummaryFormat.clean(summary), List.of(), fix, fix == null ? null : blankToNull(betterWhy),
                "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
