package com.englishtranslation.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The sign-in code last sent to one email. Only a keyed hash is saved, never the code itself. */
@Entity
@Table(name = "email_login_code")
public class EmailLoginCode {

    @Id
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailLoginCode() {
    }

    public EmailLoginCode(String email, String codeHash, Instant createdAt, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.attempts = 0;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
