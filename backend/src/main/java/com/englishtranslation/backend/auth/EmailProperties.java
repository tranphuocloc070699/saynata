package com.englishtranslation.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for sending email (app.email.*).
 *
 * @param from   the sender address, which must be verified in AWS SES (env SES_FROM_EMAIL).
 *               Empty = emails are not sent; the code is written to the log instead (dev and tests).
 * @param region the AWS region of SES (env AWS_REGION)
 */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String from, String region) {
}
