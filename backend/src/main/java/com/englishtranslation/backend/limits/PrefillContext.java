package com.englishtranslation.backend.limits;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Set only by the prefill runner, on its own thread, for the length of one run (TASK-83.1).
 * It tells ClaudeGate "this call is not for a user: skip the sign-in and the user limits, and stop at this run's
 * cap", and tells ClaudeUsageService to save the call as PREFILL, so it does not use the users' daily budget.
 */
public final class PrefillContext {

    /** @param startedAt when the run began; the run's spend is what PREFILL rows add up to since then */
    public record Run(Instant startedAt, BigDecimal maxUsd) {
    }

    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<>();

    private PrefillContext() {
    }

    public static Optional<Run> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Runs {@code work} as a prefill run on this thread, then clears the flag again. */
    public static void runAs(Run run, Runnable work) {
        Run before = CURRENT.get();
        CURRENT.set(run);
        try {
            work.run();
        } finally {
            if (before == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(before);
            }
        }
    }
}
