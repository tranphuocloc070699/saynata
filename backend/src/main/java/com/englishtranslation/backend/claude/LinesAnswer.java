package com.englishtranslation.backend.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** What Claude returns for the language lines (TASK-106.2): one line per text, in the order of the texts. */
public record LinesAnswer(
        @JsonPropertyDescription("One short natural line in the target language for each text, in the same order as the texts. Exactly as many lines as texts.") List<String> lines) {
}
