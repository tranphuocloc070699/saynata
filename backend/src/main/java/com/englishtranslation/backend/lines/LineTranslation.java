package com.englishtranslation.backend.lines;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One saved language line: a short text in one target language. Shared by all users. */
@Entity
@Table(name = "line_translation")
public class LineTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    /** The normalized text. */
    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "target_lang", nullable = false, length = 8)
    private String targetLang;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "translation", nullable = false, columnDefinition = "TEXT")
    private String translation;

    /** "llm", "codex" or "seed". */
    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LineTranslation() {
    }

    public LineTranslation(String textHash, String text, String targetLang, String promptVersion,
            String translation, String source, Instant createdAt) {
        this.textHash = textHash;
        this.text = text;
        this.targetLang = targetLang;
        this.promptVersion = promptVersion;
        this.translation = translation;
        this.source = source;
        this.createdAt = createdAt;
    }

    public String getTextHash() {
        return textHash;
    }

    public String getTranslation() {
        return translation;
    }

    public String getSource() {
        return source;
    }
}
