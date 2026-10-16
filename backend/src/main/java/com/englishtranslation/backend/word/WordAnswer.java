package com.englishtranslation.backend.word;

/** A word answer and where it came from. The controller picks the Cache-Control header by the origin. */
public record WordAnswer(WordResponse response, Origin origin) {

    public enum Origin {
        /** From the bundled dictionary in Postgres. */
        BUNDLED,
        /** A word_lookup row saved by an earlier Claude call. */
        CACHED,
        /** Claude wrote it just now. Its picture is still being fetched. */
        NEW,
        /** Not on the English word list: an empty entry, Claude not called. */
        NOT_A_WORD
    }
}
