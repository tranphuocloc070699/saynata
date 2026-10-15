package com.englishtranslation.backend.limits;

import java.util.Optional;

/**
 * Who is asking, for the thread that handles the request. BearerTokenFilter sets it and clears it at the end of
 * the request. The sentence stream works on its own thread, so TranslationController copies it there with
 * {@link #runAs}. No value means a guest (or code that runs outside a request).
 */
public final class CallerContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private CallerContext() {
    }

    public static Optional<Long> userId() {
        return Optional.ofNullable(USER_ID.get());
    }

    /** Sets the signed-in user (or null for a guest) and returns what was there before, for {@link #restore}. */
    public static Long set(Long userId) {
        Long before = USER_ID.get();
        restore(userId);
        return before;
    }

    public static void restore(Long userId) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    /** Runs {@code work} as this user (null = guest) on the current thread, then puts the old value back. */
    public static void runAs(Long userId, Runnable work) {
        Long before = set(userId);
        try {
            work.run();
        } finally {
            restore(before);
        }
    }
}
