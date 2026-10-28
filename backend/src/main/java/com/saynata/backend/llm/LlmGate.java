package com.saynata.backend.llm;

import com.saynata.backend.common.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The one door to a NEW LLM call. WordLookupService calls it inside the single-flight winner,
 * right before {@link LlmClient#explain}. Cache hits and waiting callers never reach it.
 *
 * beforeCall checks, in this order:
 *   1. Is today's money budget left?   No -> 503 budget_exhausted
 *   2. Is this IP under its hourly limit? No -> 429 rate_limited
 * Budget comes first, so a refused call does not count against anyone.
 *
 * afterSuccess saves one llm_usage row. Failed calls are not recorded.
 * The state lives in the database, not in memory, so it survives a restart and works with many instances.
 * Small gap on purpose: two different words from one IP at the same moment can both pass the check.
 * The overshoot is at most a few calls; a strict lock is not worth the complexity (YAGNI).
 */
@Component
public class LlmGate {

    private final LlmUsageRepository usage;
    private final LlmProperties props;

    public LlmGate(LlmUsageRepository usage, LlmProperties props) {
        this.usage = usage;
        this.props = props;
    }

    public void beforeCall(String ip) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal spent = usage.spentSince(startOfDay);
        if (spent.add(props.costPerCallUsd()).compareTo(props.dailyBudgetUsd()) > 0) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "budget_exhausted",
                    "New words are paused for today. Words we already know still work.");
        }
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        if (usage.countByIpAndCreatedAtAfter(ip, hourAgo) >= props.maxCallsPerIpPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Too many new words in a short time. Please try again later.");
        }
    }

    public void afterSuccess(String ip, String headword) {
        usage.save(new LlmUsage(ip, headword, props.costPerCallUsd()));
    }
}
