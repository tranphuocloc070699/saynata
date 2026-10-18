package com.englishtranslation.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The sign-in link last emailed to one address. Only hashes are saved: of the link token and of the app's secret. */
@Entity
@Table(name = "email_login_link")
public class EmailLoginLink {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "code_challenge", nullable = false, length = 64)
    private String codeChallenge;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EmailLoginLink() {
    }

    public EmailLoginLink(String tokenHash, String email, String codeChallenge, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.codeChallenge = codeChallenge;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
