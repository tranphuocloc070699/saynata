package com.englishtranslation.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fake sender: writes the code to the log instead of sending an email. Used when no mail service is set
 * (local dev, tests, and production before Resend or SES is ready). On a server this puts codes in the log, so a warning
 * is logged at start-up (see EmailConfig).
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendSignInCode(String toEmail, String code, int validMinutes) {
        log.info("[fake email] To: {} | {} | code: {} (valid {} minutes)", toEmail, EmailSender.subject(), code,
                validMinutes);
    }

    @Override
    public void sendSignInLink(String toEmail, String link, int validMinutes) {
        log.info("[fake email] To: {} | {} | link: {} (valid {} minutes)", toEmail, EmailSender.linkSubject(), link,
                validMinutes);
    }
}
