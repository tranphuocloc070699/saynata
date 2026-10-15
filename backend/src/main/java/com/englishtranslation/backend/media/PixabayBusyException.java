package com.englishtranslation.backend.media;

/** Pixabay answered 429 (too many requests) on every try. The caller can ask the admin to try again later. */
public class PixabayBusyException extends IllegalStateException {

    public PixabayBusyException(String message) {
        super(message);
    }
}
