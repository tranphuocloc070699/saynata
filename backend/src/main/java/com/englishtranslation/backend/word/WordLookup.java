package com.englishtranslation.backend.word;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A word the bundled dictionary did not have, described by Claude and saved for every user. */
@Entity
@Table(name = "word_lookup")
public class WordLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 64)
    private String cacheKey;

    @Column(name = "word", nullable = false, length = 64)
    private String word;

    @Column(name = "entry_json", nullable = false, columnDefinition = "TEXT")
    private String entryJson;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 16)
    private String promptVersion;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    /** Level of the first sense (A1 to C2), so the admin list can filter by level. Old rows get it at start-up. */
    @Column(name = "cefr", length = 8)
    private String cefr;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordLookup() {
    }

    public WordLookup(String cacheKey, String word, String entryJson, String model, String promptVersion) {
        this(cacheKey, word, entryJson, model, promptVersion, false);
    }

    /** needsReview is true when the entry still had hard words after the retry (TASK-16). */
    public WordLookup(String cacheKey, String word, String entryJson, String model, String promptVersion,
            boolean needsReview) {
        this.needsReview = needsReview;
        this.cacheKey = cacheKey;
        this.word = word;
        this.entryJson = entryJson;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = Instant.now();
    }

    public String getCefr() {
        return cefr;
    }

    public void setCefr(String cefr) {
        this.cefr = cefr;
    }

    public Long getId() {
        return id;
    }

    public String getWord() {
        return word;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public String getEntryJson() {
        return entryJson;
    }

    /** Used by the admin web app when a person fixes the entry. */
    public void setEntryJson(String entryJson) {
        this.entryJson = entryJson;
    }
}
