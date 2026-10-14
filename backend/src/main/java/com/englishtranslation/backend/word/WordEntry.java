package com.englishtranslation.backend.word;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Same shape as pipeline/src/pipeline/models.py, in camelCase and without the image fields. */
public record WordEntry(List<Sense> senses) {

    /** The level of the first (main) sense, or null when there is none. The admin list filters on it. */
    public String firstCefr() {
        return senses == null || senses.isEmpty() || senses.get(0) == null ? null : senses.get(0).cefr();
    }

    public record Sense(
            String pos,
            String cefr,
            String simpleDefinition,
            String vietnamese,
            @JsonPropertyDescription("Exactly 3 examples of this sense, ordered easy, medium, harder.")
            List<Example> examples,
            @JsonPropertyDescription("0-3 common, natural ways to use this sense. Fewer, or none, when there are no good ones.")
            List<Usage> usage) {
    }

    /**
     * One example sentence. level is a String, not an enum, for the same reason as Usage.kind:
     * exactly one of easy, medium, harder (lowercase).
     */
    public record Example(
            @JsonPropertyDescription("Exactly one of: easy, medium, harder")
            String level,
            String text) {
    }

    /**
     * One way to use a sense: a pattern and one example sentence.
     * kind is a String, not an enum: the Anthropic schema generator writes enum names in capitals
     * (PATTERN), but the app and the dictionary use lowercase.
     */
    public record Usage(
            @JsonPropertyDescription("Exactly one of: pattern, collocation, phrase (a phrasal verb or fixed phrase), idiom")
            String kind,
            String pattern,
            String example) {
    }
}
