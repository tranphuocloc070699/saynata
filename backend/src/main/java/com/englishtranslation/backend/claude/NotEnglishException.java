package com.englishtranslation.backend.claude;

/** The text is not English (code, gibberish, another language). It becomes HTTP 422, and nothing is cached. */
public class NotEnglishException extends RuntimeException {

    public static final String MESSAGE = "This doesn't look like English. Type an English word or sentence.";

    public NotEnglishException() {
        super(MESSAGE);
    }
}
