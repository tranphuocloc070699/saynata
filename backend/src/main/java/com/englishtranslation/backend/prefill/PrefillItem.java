package com.englishtranslation.backend.prefill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One word or phrase waiting to be answered before a user asks for it (table prefill_item, Flyway V17).
 * {@code text} is already normalised: a word with CacheKeys.normalizeWord, a sentence or phrase with
 * CacheKeys.normalizeSentence. {@code targetLang} is empty for a word and a TargetLanguage code for a sentence.
 */
@Entity
@Table(name = "prefill_item")
public class PrefillItem {

    public static final String PENDING = "pending";
    public static final String DONE = "done";
    public static final String SKIPPED = "skipped";
    public static final String FAILED = "failed";

    public static final String WORD = "word";
    public static final String SENTENCE = "sentence";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "seen_count", nullable = false)
    private int seenCount = 1;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrefillItem() {
    }

    public PrefillItem(String kind, String text, String targetLang, String source, int priority, Instant now) {
        this.kind = kind;
        this.text = text;
        this.targetLang = targetLang;
        this.source = source;
        this.priority = priority;
        this.status = PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Sets the new status and the time. {@code error} is kept for the admin to read (null clears it). */
    public void finish(String newStatus, String error, Instant now) {
        this.status = newStatus;
        this.lastError = error;
        this.updatedAt = now;
    }

    /**
     * Another sentence contained this word (TASK-83.8): one more sighting, and a higher priority if the new one is
     * higher. A priority never goes down, so a pipeline priority above the new one is kept.
     */
    public void seenAgain(int newPriority) {
        this.seenCount++;
        this.priority = Math.max(this.priority, newPriority);
    }

    /** A failed try: one more attempt counted, the error saved. */
    public void fail(String error, Instant now) {
        this.attempts++;
        finish(FAILED, error, now);
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public String getSource() {
        return source;
    }

    public int getSeenCount() {
        return seenCount;
    }

    public int getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
