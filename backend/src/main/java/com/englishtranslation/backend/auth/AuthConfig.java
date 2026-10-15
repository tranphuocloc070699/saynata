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
     * SES when SES_FROM_EMAIL is set, the logging fake otherwise. The fake keeps the backend starting (and
     * sign-in testable with the log) before SES is ready. A real deploy without SES logs a warning.
     */
    @Bean
    public EmailSender emailSender(EmailProperties properties) {
        if (properties.from() == null || properties.from().isBlank()) {
            log.warn("SES_FROM_EMAIL is not set. Sign-in codes are NOT emailed; they are written to this log. "
                    + "Set SES_FROM_EMAIL and AWS_REGION (and the AWS keys) to send real email.");
            return new LoggingEmailSender();
        }
        log.info("Sign-in codes are sent with AWS SES from {} (region {}).", properties.from(), properties.region());
        return new SesEmailSender(properties.from(), properties.region());
    }
}
