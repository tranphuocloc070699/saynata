package com.saynata.backend.word;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the "word" table (see V1__create_word.sql). Never sent to the client directly. */
@Entity
@Table(name = "word")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String headword;

    @Column(name = "part_of_speech", length = 30)
    private String partOfSpeech;

    @Column(length = 100)
    private String ipa;

    @Column(name = "simple_explanation", nullable = false)
    private String simpleExplanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA needs a no-argument constructor. Protected, so normal code cannot use it. */
    protected Word() {
    }

    public Word(String headword, String partOfSpeech, String ipa, String simpleExplanation) {
        this.headword = headword;
        this.partOfSpeech = partOfSpeech;
        this.ipa = ipa;
        this.simpleExplanation = simpleExplanation;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getHeadword() {
        return headword;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public String getIpa() {
        return ipa;
    }

    public String getSimpleExplanation() {
        return simpleExplanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
