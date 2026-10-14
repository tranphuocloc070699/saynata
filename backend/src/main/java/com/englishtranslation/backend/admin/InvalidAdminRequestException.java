package com.englishtranslation.backend.admin;

/** The admin sent something we cannot accept. The message is shown to them as the 400 detail. */
public class InvalidAdminRequestException extends RuntimeException {

    public InvalidAdminRequestException(String message) {
        super(message);
    }
}
