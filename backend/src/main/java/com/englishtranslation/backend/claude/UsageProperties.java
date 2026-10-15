package com.englishtranslation.backend.claude;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Money settings for Claude (app.claude.* in application.properties; the model and prompt versions are in
 * {@link ClaudeProperties}).
 *
 * @param dailyBudgetUsd the most we spend on Claude per UTC day; at this point new calls stop
 * @param pricing        price per model id, in US dollars per million tokens
 */
@ConfigurationProperties(prefix = "app.claude")
public record UsageProperties(BigDecimal dailyBudgetUsd, Map<String, Price> pricing) {

    public record Price(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {
    }
}
