package com.englishtranslation.backend.prefill;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the prefill job (app.prefill.* in application.properties).
 *
 * @param cron          when the job runs by itself, a Spring cron (six fields) in UTC; empty = it never runs by itself
 * @param maxUsdPerRun  the most one run may spend on Claude, in US dollars (its own cap, not the users' budget)
 * @param delay         the pause after each item that made a NEW word, so the Pixabay picture jobs and the
 *                      thread pool are not flooded (Pixabay allows 100 requests a minute)
 * @param maxAttempts   a failed item is tried again by later runs until it has failed this many times
 */
@ConfigurationProperties(prefix = "app.prefill")
public record PrefillProperties(String cron, BigDecimal maxUsdPerRun, Duration delay, int maxAttempts) {

    public PrefillProperties {
        cron = cron == null ? "" : cron.strip();
        maxUsdPerRun = maxUsdPerRun == null ? new BigDecimal("0.50") : maxUsdPerRun;
        delay = delay == null ? Duration.ofSeconds(3) : delay;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    }
}
