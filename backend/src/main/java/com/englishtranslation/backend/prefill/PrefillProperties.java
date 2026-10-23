package com.englishtranslation.backend.prefill;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the prefill job (app.prefill.* in application.properties).
 *
 * @param cron          when the job runs by itself, a Spring cron (six fields) in UTC; empty = it never runs by itself
 * @param cronSources   the queue sources the scheduled run answers (TASK-83.12, {@code app.prefill.cron-sources}).
 *                      Other sources stay pending at night; they are run by hand. Default: sentence-words
 * @param maxUsdPerRun  the most one run may spend on Claude, in US dollars (its own cap, not the users' budget)
 * @param delay         an optional pause after each item that made a NEW word (default none). Prefilled words get
 *                      no picture, so nothing else needs the pause; it only slows a run down on purpose
 * @param maxAttempts   a failed item is tried again by later runs until it has failed this many times
 */
@ConfigurationProperties(prefix = "app.prefill")
public record PrefillProperties(String cron, List<String> cronSources, BigDecimal maxUsdPerRun, Duration delay, int maxAttempts) {

    public PrefillProperties {
        cron = cron == null ? "" : cron.strip();
        cronSources = cronSources == null ? List.of(SentenceWordQueue.SOURCE)
                : cronSources.stream().map(String::strip).filter(source -> !source.isEmpty()).toList();
        maxUsdPerRun = maxUsdPerRun == null ? new BigDecimal("0.50") : maxUsdPerRun;
        delay = delay == null ? Duration.ZERO : delay;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    }
}
