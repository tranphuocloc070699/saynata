package com.saynata.backend.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One row of the "llm_usage" table (see V2__create_llm_usage.sql). */
@Entity
@Table(name = "llm_usage")
public class LlmUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(nullable = false, length = 100)
    private String headword;

    @Column(name = "cost_usd", nullable = false)
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmUsage() {
    }

    public LlmUsage(String ip, String headword, BigDecimal costUsd) {
        this.ip = ip;
        this.headword = headword;
        this.costUsd = costUsd;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
