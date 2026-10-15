package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.limits.RateLimiter.Limit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limits (app.limits.* in application.properties). Every rule is "max requests per period".
 *
 * @param emailCodePerIp    POST /api/auth/email-code, per client IP
 * @param emailCodePerEmail POST /api/auth/email-code, per email address
 * @param claudePerUserHour new Claude calls, per signed-in user
 * @param claudePerUserDay  new Claude calls, per signed-in user
 * @param publicReadPerIp   /api/translations* and /api/words/*, per client IP (cache hits included)
 */
@ConfigurationProperties(prefix = "app.limits")
public record LimitProperties(
        Rule emailCodePerIp,
        Rule emailCodePerEmail,
        Rule claudePerUserHour,
        Rule claudePerUserDay,
        Rule publicReadPerIp) {

    public record Rule(int max, Duration period) {
        Limit toLimit(String name) {
            return new Limit(name, max, period);
        }
    }

    public Limit emailCodePerIpLimit() {
        return emailCodePerIp.toLimit("email-code-ip");
    }

    public Limit emailCodePerEmailLimit() {
        return emailCodePerEmail.toLimit("email-code-email");
    }

    public Limit claudePerUserHourLimit() {
        return claudePerUserHour.toLimit("claude-user-hour");
    }

    public Limit claudePerUserDayLimit() {
        return claudePerUserDay.toLimit("claude-user-day");
    }

    public Limit publicReadPerIpLimit() {
        return publicReadPerIp.toLimit("public-read-ip");
    }
}
