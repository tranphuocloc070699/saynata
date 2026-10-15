package com.englishtranslation.backend.auth;

/** Sends the sign-in code. Behind an interface so tests and dev need no AWS. */
public interface EmailSender {

    /** Sends the code. May throw if the mail service fails; the caller logs it and still answers 204. */
    void sendSignInCode(String toEmail, String code, int validMinutes);

    /** The subject and plain text body, shared by every sender so they say the same simple thing. */
    static String subject() {
        return "Your Saynata sign-in code";
    }

    static String body(String code, int validMinutes) {
        return "Your Saynata sign-in code is " + code + ".\n\n"
                + "It works for " + validMinutes + " minutes. You can use it once.\n\n"
                + "If you did not ask for this code, you can ignore this email.\n";
    }
}
