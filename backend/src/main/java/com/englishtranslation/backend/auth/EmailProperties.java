package com.englishtranslation.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for sending email (app.email.*).
 *
 * @param from         the sender address on a domain verified at the mail service (env EMAIL_FROM). Empty = emails are not sent; the code is written to the log (dev and tests).
 * @param region       the AWS region of SES (env AWS_REGION), only for SES
 * @param sesFrom      the older name for the sender address (env SES_FROM_EMAIL), used when {@code from} is empty
 * @param resendApiKey the Resend API key (env RESEND_API_KEY). Set = Resend is used (when {@code from} is set too).
 */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String from, String region, String resendApiKey, String sesFrom) {

    /** The sender address to use: EMAIL_FROM, else SES_FROM_EMAIL, else empty. */
    public String fromAddress() {
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        return sesFrom == null ? "" : sesFrom.trim();
    }
}
