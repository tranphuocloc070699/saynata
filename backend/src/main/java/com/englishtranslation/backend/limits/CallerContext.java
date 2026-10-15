package com.englishtranslation.backend.limits;

import java.util.Optional;

/**
 * Who is asking, for the thread that handles the request: the signed-in user id (none = guest) and the client IP.
 * BearerTokenFilter sets it and clears it at the end of the request. The sentence stream works on its own thread,
 * so TranslationController copies it there with {@link #runAs}. No value at all means code that runs outside a request.
 */
public final class CallerContext {

    /** {@code userId} is null for a guest. {@code ip} is null outside a web request. */
    public record Caller(Long userId, String ip) {
    }

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    private CallerContext() {
    }

    public static Optional<Long> userId() {
        return current().map(Caller::userId);
    }

    public static Optional<String> ip() {
        return current().map(Caller::ip);
    }

    public static Optional<Caller> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Sets the caller and returns what was there before, for {@link #restore}. */
    public static Caller set(Long userId, String ip) {
        Caller before = CURRENT.get();
        CURRENT.set(new Caller(userId, ip));
        return before;
    }

    public static void restore(Caller before) {
        if (before == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(before);
        }
    }

    /** Runs {@code work} as this caller on the current thread, then puts the old value back. */
    public static void runAs(Caller caller, Runnable work) {
        Caller before = caller == null ? null : set(caller.userId(), caller.ip());
        try {
            work.run();
        } finally {
            restore(before);
        }
    }
}
