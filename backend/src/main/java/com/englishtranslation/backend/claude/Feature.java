package com.englishtranslation.backend.claude;

/** Which part of Saynata made a Claude call. Saved with the usage, so the spend can be split. */
public enum Feature {
    SENTENCE,
    WORD,
    /** For admin tools that call Claude. None does yet, but the budget cap covers them when one does. */
    ADMIN
}
