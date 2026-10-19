package com.englishtranslation.backend.liked;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One word or sentence a user starred. The key is (user, kind, textKey). */
@Entity
@Table(name = "liked_item")
public class LikedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "word" or "sentence". */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** The normalized text, used only to find the same item again. */
    @Column(name = "text_key", nullable = false, length = 500)
    private String textKey;

    /** The text as the user saw it. */
    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "preview", nullable = false, length = 300)
    private String preview;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LikedItem() {
    }

    public LikedItem(Long userId, String kind, String textKey, String text, String preview, Instant createdAt) {
        this.userId = userId;
        this.kind = kind;
        this.textKey = textKey;
        this.text = text;
        this.preview = preview;
        this.createdAt = createdAt;
    }

    public String getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getPreview() {
        return preview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(String text, String preview) {
        this.text = text;
        this.preview = preview;
    }
}
