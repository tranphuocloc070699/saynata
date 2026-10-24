package com.englishtranslation.backend.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** One account. The admin password is saved as a bcrypt hash, never as text. App users have no password. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Empty for app users: they sign in with an email code, so they have no password. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // EAGER: open-in-view is off, so roles must be loaded together with the user.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Set while the account is blocked (TASK-105): no new Claude call, cached answers still work. */
    @Column(name = "blocked_at")
    private Instant blockedAt;

    /** Why the admin blocked it. Only admins see it. */
    @Column(name = "blocked_reason", length = 300)
    private String blockedReason;

    /** The last sign-in or token refresh, saved at most once an hour (TASK-105). */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roles.add(role);
        this.createdAt = Instant.now();
    }

    /** A new app user who signed in with an email code for the first time. */
    public static User appUser(String email) {
        String name = email.substring(0, email.indexOf('@'));
        return new User(email, null, name.length() > 100 ? name.substring(0, 100) : name, Role.USER);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public boolean isBlocked() {
        return blockedAt != null;
    }

    public void block(String reason, Instant now) {
        this.blockedAt = now;
        this.blockedReason = reason;
    }

    public void unblock() {
        this.blockedAt = null;
        this.blockedReason = null;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /** True when last seen is empty or older than {@code every}; then it is set to now and the caller saves it. */
    public boolean markSeen(Instant now, Duration every) {
        if (lastSeenAt != null && lastSeenAt.isAfter(now.minus(every))) {
            return false;
        }
        lastSeenAt = now;
        return true;
    }
}
