package org.humsaadhak.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.humsaadhak.shared.entity.AuditedEntity;

/** Invitation or password-reset token. Only the SHA-256 hash is stored; the raw value lives in the email. */
@Entity
@Table(name = "one_time_token")
public class OneTimeToken extends AuditedEntity {
    public enum Purpose { INVITATION, PASSWORD_RESET }

    @Column(name = "user_id", nullable = false) private long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Purpose purpose;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;

    protected OneTimeToken() {}

    public OneTimeToken(long userId, Purpose purpose, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) { return usedAt == null && now.isBefore(expiresAt); }
    public void markUsed(Instant now) { usedAt = now; }
    public long getUserId() { return userId; }
    public Purpose getPurpose() { return purpose; }
}
