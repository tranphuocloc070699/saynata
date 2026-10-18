package com.englishtranslation.backend.aipicture;

/** No picture was made. The message is a plain sentence the admin page shows. */
public class DrawException extends Exception {

    public DrawException(String message) {
        super(message);
    }

    public DrawException(String message, Throwable cause) {
        super(message, cause);
    }
}
