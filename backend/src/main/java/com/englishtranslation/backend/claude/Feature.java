package com.englishtranslation.backend.claude;

/** Which part of Saynata made a Claude call. Saved with the usage, so the spend can be split. */
public enum Feature {
    SENTENCE,
    WORD,
    /** For admin tools that call Claude. None does yet, but the budget cap covers them when one does. */
    ADMIN,
    /** The tiny call at start-up that warms the connection (TASK-75). It costs about nothing. */
    WARMUP,
    /** The nightly prefill job (TASK-83.1). It has its own cap and does not count toward the users' daily budget. */
    PREFILL,
    /** The language line for a short text (TASK-106.2, POST /api/translate-lines). Counts toward the users' budget. */
    LINE
}
