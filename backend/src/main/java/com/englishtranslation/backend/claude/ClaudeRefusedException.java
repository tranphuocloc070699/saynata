package com.englishtranslation.backend.claude;

public class ClaudeRefusedException extends RuntimeException {

    public ClaudeRefusedException(String message) {
        super(message);
    }
}
