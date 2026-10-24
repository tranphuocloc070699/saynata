package com.englishtranslation.backend.sources;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One uploaded book (table source_upload, Flyway V28): the title, the counts and the candidate lines. The text of
 * the book is never saved. {@code candidates} is one line per candidate, see {@link SourceCandidate#toLine}.
 */
@Entity
@Table(name = "source_upload")
public class SourceUpload {

    public static final String READY = "ready";
    public static final String QUEUED = "queued";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "words_in_file", nullable = false)
    private int wordsInFile;

    @Column(name = "known", nullable = false)
    private int known;

    @Column(name = "new_words", nullable = false)
    private int newWords;

    @Column(name = "new_phrases", nullable = false)
    private int newPhrases;

    @Column(name = "names_skipped", nullable = false)
    private int namesSkipped;

    @Column(name = "candidates", nullable = false, columnDefinition = "TEXT")
    private String candidates;

    @Column(name = "status", nullable = false, length = 10)
    private String status = READY;

    @Column(name = "queued_count", nullable = false)
    private int queuedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    protected SourceUpload() {
    }

    public SourceUpload(String title, int wordsInFile, int known, int newWords, int newPhrases, int namesSkipped,
            String candidates, Instant now) {
        this.title = title;
        this.wordsInFile = wordsInFile;
        this.known = known;
        this.newWords = newWords;
        this.newPhrases = newPhrases;
        this.namesSkipped = namesSkipped;
        this.candidates = candidates;
        this.createdAt = now;
    }

    /** After queueing: the snippets (the only pieces of the book) are removed and the upload cannot be used again. */
    public void markQueued(String candidatesWithoutSnippets, int count, Instant now) {
        this.candidates = candidatesWithoutSnippets;
        this.status = QUEUED;
        this.queuedCount = count;
        this.queuedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getWordsInFile() {
        return wordsInFile;
    }

    public int getKnown() {
        return known;
    }

    public int getNewWords() {
        return newWords;
    }

    public int getNewPhrases() {
        return newPhrases;
    }

    public int getNamesSkipped() {
        return namesSkipped;
    }

    public String getCandidates() {
        return candidates;
    }

    public String getStatus() {
        return status;
    }

    public int getQueuedCount() {
        return queuedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
