package com.englishtranslation.backend.dictionary;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A word of the bundled dictionary (the NGSL words in the iOS app). The pipeline fills these tables with
 * "import-to-postgres" and builds the next dictionary.sqlite from them with "export".
 */
@Entity
@Table(name = "dictionary_word")
public class DictionaryWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lemma", nullable = false, length = 64)
    private String lemma;

    @Column(name = "word_rank", nullable = false)
    private int rank;

    @Column(name = "ipa", length = 128)
    private String ipa;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "prompt_version", length = 16)
    private String promptVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position")
    private List<DictionarySense> senses = new ArrayList<>();

    protected DictionaryWord() {
    }

    /** Used by tests. The real rows come from the pipeline import. */
    public DictionaryWord(String lemma, int rank, String ipa) {
        this.lemma = lemma;
        this.rank = rank;
        this.ipa = ipa;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getLemma() {
        return lemma;
    }

    public int getRank() {
        return rank;
    }

    public String getIpa() {
        return ipa;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
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

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<DictionarySense> getSenses() {
        return senses;
    }
}
