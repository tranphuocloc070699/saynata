package com.englishtranslation.backend.auth;

/** Sends the sign-in emails (a code, or a link). Behind an interface so tests and dev need no mail service. */
public interface EmailSender {

    /** Sends the code. May throw if the mail service fails; the caller logs it and still answers 204. */
    void sendSignInCode(String toEmail, String code, int validMinutes);

    /** Sends the sign-in link. May throw like {@link #sendSignInCode}. */
    void sendSignInLink(String toEmail, String link, int validMinutes);

    /** The subject and plain text body, shared by every sender so they say the same simple thing. */
    static String subject() {
        return "Your Saynata sign-in code";
    }

    static String body(String code, int validMinutes) {
        return "Your Saynata sign-in code is " + code + ".\n\n"
                + "It works for " + validMinutes + " minutes. You can use it once.\n\n"
                + "If you did not ask for this code, you can ignore this email.\n";
    }

    static String linkSubject() {
        return "Sign in to Saynata";
    }

    static String linkBody(String link, int validMinutes) {
        return "Tap this link on your iPhone to sign in to Saynata:\n\n"
                + link + "\n\n"
                + "It works for " + validMinutes + " minutes, once, in the app where you asked for it.\n\n"
                + "If you did not ask for this link, you can ignore this email.\n";
    }

    /** The link is our own address with a URL-safe token, so it needs no HTML escaping. */
    static String linkHtml(String link, int validMinutes) {
        return "<p>Tap the button on your iPhone to sign in to Saynata.</p>"
                + "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 20px;border-radius:10px;"
                + "background:#0f766e;color:#ffffff;text-decoration:none;font-weight:600\">Sign in to Saynata</a></p>"
                + "<p>It works for " + validMinutes + " minutes, once, in the app where you asked for it.</p>"
                + "<p>If you did not ask for this link, you can ignore this email.</p>";
    }
}
