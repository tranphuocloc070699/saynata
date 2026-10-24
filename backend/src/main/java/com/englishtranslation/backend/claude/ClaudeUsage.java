package com.englishtranslation.backend.claude;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One Claude call: the tokens Claude reported and the cost we worked out from the config prices. */
@Entity
@Table(name = "claude_usage")
public class ClaudeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 20)
    private Feature feature;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 14, scale = 8)
    private BigDecimal costUsd;

    /** The signed-in user who caused the call (TASK-105); null for the night job, guests and old rows. */
    @Column(name = "user_id")
    private Long userId;

    protected ClaudeUsage() {
    }

    public ClaudeUsage(Instant createdAt, String model, Feature feature, long inputTokens, long outputTokens,
            BigDecimal costUsd) {
        this.createdAt = createdAt;
        this.model = model;
        this.feature = feature;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
    }

    public ClaudeUsage(Instant createdAt, String model, Feature feature, long inputTokens, long outputTokens,
            BigDecimal costUsd, Long userId) {
        this(createdAt, model, feature, inputTokens, outputTokens, costUsd);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getModel() {
        return model;
    }

    public Feature getFeature() {
        return feature;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }
}
