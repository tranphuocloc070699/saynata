package com.englishtranslation.backend.suggest;

/** One line of the list while typing. kind is "word" or "phrase". */
public record Suggestion(String text, String kind) {

    static Suggestion word(String text) {
        return new Suggestion(text, "word");
    }

    static Suggestion phrase(String text) {
        return new Suggestion(text, "phrase");
    }
}
