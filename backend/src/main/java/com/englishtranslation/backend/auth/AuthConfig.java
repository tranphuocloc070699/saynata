package com.englishtranslation.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ AuthProperties.class, EmailProperties.class })
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    /**
     * Picks the sender: Resend when RESEND_API_KEY and a from address are set, else SES when a from address is
     * set, else the logging fake. The fake keeps the backend starting (and sign-in testable with the log) when no
     * mail service is set up. A real deploy without one logs a warning.
     */
    @Bean
    public EmailSender emailSender(EmailProperties properties) {
        String from = properties.fromAddress();
        boolean hasFrom = !from.isBlank();
        boolean hasResendKey = properties.resendApiKey() != null && !properties.resendApiKey().isBlank();
        if (hasResendKey && hasFrom) {
            log.info("Sign-in codes are sent with Resend from {}.", from);
            return new ResendEmailSender(properties.resendApiKey().trim(), from);
        }
        if (hasResendKey) {
            log.warn("RESEND_API_KEY is set but EMAIL_FROM is not. Resend is NOT used. Set EMAIL_FROM.");
        }
        if (hasFrom) {
            log.info("Sign-in codes are sent with AWS SES from {} (region {}).", from,
                    properties.region());
            return new SesEmailSender(from, properties.region());
        }
        log.warn("No email service is set. Sign-in codes are NOT emailed; they are written to this log. "
                + "Set RESEND_API_KEY and EMAIL_FROM (or SES_FROM_EMAIL and the AWS variables) to send real email.");
        return new LoggingEmailSender();
    }
}
