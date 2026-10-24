package com.englishtranslation.backend.translation;

import com.englishtranslation.backend.cache.CacheKeys;
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

    /** Loose key (case and final punctuation ignored), not unique. See CacheKeys.sentenceMatchKey. */
    @Column(name = "match_key", nullable = false, length = 64)
    private String matchKey;

    @Column(name = "source_text", nullable = false, length = 500)
    private String sourceText;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "words_json", nullable = false, columnDefinition = "TEXT")
    private String wordsJson;

    /** The text with its mistakes fixed (TASK-110, prompt v10); null when it is natural or the row is older. */
    @Column(name = "better", columnDefinition = "TEXT")
    private String better;

    @Column(name = "better_why", columnDefinition = "TEXT")
    private String betterWhy;

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
        this(cacheKey, sourceText, targetLang, meaning, wordsJson, null, null, translation, model, promptVersion);
    }

    public SentenceTranslation(String cacheKey, String sourceText, String targetLang, String meaning, String wordsJson,
            String better, String betterWhy, String translation, String model, String promptVersion) {
        this.cacheKey = cacheKey;
        this.matchKey = CacheKeys.sentenceMatchKey(promptVersion, targetLang, sourceText);
        this.sourceText = sourceText;
        this.targetLang = targetLang;
        this.meaning = meaning;
        this.wordsJson = wordsJson;
        this.better = better;
        this.betterWhy = betterWhy;
        this.translation = translation;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMatchKey() {
        return matchKey;
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

    public String getBetter() {
        return better;
    }

    public String getBetterWhy() {
        return betterWhy;
    }

    public String getTranslation() {
        return translation;
    }
}
