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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordLookup() {
    }

    public WordLookup(String cacheKey, String word, String entryJson, String model, String promptVersion) {
        this.cacheKey = cacheKey;
        this.word = word;
        this.entryJson = entryJson;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = Instant.now();
    }

    public String getEntryJson() {
        return entryJson;
    }
}
