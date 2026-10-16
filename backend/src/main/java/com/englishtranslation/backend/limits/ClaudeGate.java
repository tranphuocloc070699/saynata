package com.englishtranslation.backend.limits;

import com.englishtranslation.backend.auth.AuthProperties;
import com.englishtranslation.backend.claude.ClaudeUsageService;
import org.springframework.stereotype.Component;

/**
 * The one door to a new Claude call. Services call it right before they ask Claude, and only then (cache hits
 * and bundled words never reach it). It checks, in this order:
 * <ol>
 * <li>Is the caller signed in? A guest gets 401 {@code sign_in_required}.</li>
 * <li>Is today's money budget left? If not, 503 {@code daily_limit_reached}.</li>
 * <li>Is the user under the hourly and daily limit? If not, 429 {@code rate_limited}. When sign-in is not required
 * (REQUIRE_SIGN_IN=false) a guest has its own, smaller per-IP limit, so guests cannot burn the budget alone.</li>
 * </ol>
 * The budget comes before the user limit, so a refused call does not use up a user's allowance.
 */
@Component
public class ClaudeGate {

    private final AuthProperties auth;
    private final ClaudeUsageService usage;
    private final RateLimiter rateLimiter;
    private final LimitProperties limits;

    public ClaudeGate(AuthProperties auth, ClaudeUsageService usage, RateLimiter rateLimiter, LimitProperties limits) {
        this.auth = auth;
        this.usage = usage;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
    }

    /** Call this before every NEW Claude call for a person's request. */
    public void beforeNewCall() {
        if (checkPrefillCap()) {
            return;
        }
        Long userId = CallerContext.userId().orElse(null);
        if (userId == null && auth.requireSignIn()) {
            throw new SignInRequiredException();
        }
        checkBudget();
        if (userId != null) {
            rateLimiter.acquireOrThrow(String.valueOf(userId), limits.claudePerUserHourLimit(),
                    limits.claudePerUserDayLimit());
        } else {
            // A guest got here only because sign-in is not required. No IP = not a web request (internal code).
            String ip = CallerContext.ip().orElse(null);
            if (ip != null) {
                rateLimiter.acquireOrThrow(ip, limits.claudePerGuestIpLimit());
            }
        }
    }

    /**
     * The prefill job (TASK-83.1) has no user. It skips the sign-in, the user limits and the users' daily budget,
     * and is stopped by its own run cap instead. Returns true when this thread is a prefill run (cap checked, ok).
     */
    private boolean checkPrefillCap() {
        PrefillContext.Run run = PrefillContext.current().orElse(null);
        if (run == null) {
            return false;
        }
        if (usage.prefillSpentSince(run.startedAt()).compareTo(run.maxUsd()) >= 0) {
            throw new PrefillCapReachedException();
        }
        return true;
    }

    /**
     * Only the money check. For a second call inside one request (the word retry) and for admin tools:
     * the person already passed {@link #beforeNewCall} for this request.
     */
    public void checkBudget() {
        if (checkPrefillCap()) {
            return;
        }
        if (usage.budgetReached()) {
            throw new DailyLimitReachedException();
        }
    }
}
