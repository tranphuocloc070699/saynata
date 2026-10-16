package com.englishtranslation.backend.media;

/** The converter ran, but could not read the file: it is broken or not really a picture or clip. */
public class UnreadableMediaException extends IllegalStateException {

    public UnreadableMediaException(String message) {
        super(message);
    }
}
