package com.saynata.backend.word;

import java.time.Instant;

/** What the API returns. The shape of the API is decided here, not by the database table. */
public record WordResponse(
        Long id,
        String headword,
        String partOfSpeech,
        String ipa,
        String simpleExplanation,
        Instant createdAt) {

    static WordResponse from(Word word) {
        return new WordResponse(
                word.getId(),
                word.getHeadword(),
                word.getPartOfSpeech(),
                word.getIpa(),
                word.getSimpleExplanation(),
                word.getCreatedAt());
    }
}
