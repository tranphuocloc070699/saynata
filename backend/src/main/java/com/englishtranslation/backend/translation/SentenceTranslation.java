package com.englishtranslation.backend.translation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sentence_translation")
public class SentenceTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 64)
    private String cacheKey;

    @Column(name = "source_text", nullable = false, length = 500)
    private String sourceText;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "words_json", nullable = false, columnDefinition = "TEXT")
    private String wordsJson;

    @Column(name = "translation", nullable = false, columnDefinition = "TEXT")
    private String translation;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 16)
    private String promptVersion;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SentenceTranslation() {
    }

    public SentenceTranslation(String cacheKey, String sourceText, String targetLang, String meaning, String wordsJson,
            String translation, String model, String promptVersion) {
        this.cacheKey = cacheKey;
        this.sourceText = sourceText;
        this.targetLang = targetLang;
        this.meaning = meaning;
        this.wordsJson = wordsJson;
        this.translation = translation;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTargetLang() {
        return targetLang;
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

    /** Used by the admin web app when a person fixes the explanation. */
    public void setExplanation(String meaning, String wordsJson, String translation) {
        this.meaning = meaning;
        this.wordsJson = wordsJson;
        this.translation = translation;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getWordsJson() {
        return wordsJson;
    }

    public String getTranslation() {
        return translation;
    }
}
