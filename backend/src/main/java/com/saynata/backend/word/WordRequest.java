package com.saynata.backend.word;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the client sends to create a word. Limits match the column sizes in the database. */
public record WordRequest(
        @NotBlank @Size(max = 100) String headword,
        @Size(max = 30) String partOfSpeech,
        @Size(max = 100) String ipa,
        @NotBlank String simpleExplanation) {
}
