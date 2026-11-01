package org.humsaadhak.shared.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Identity, audit columns and optimistic locking shared by every table. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @CreatedBy @Column(name = "created_by", updatable = false)
    private String createdBy;
    @LastModifiedDate @Column(name = "updated_at")
    private Instant updatedAt;
    @LastModifiedBy @Column(name = "updated_by")
    private String updatedBy;
    @Version
    private long version;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
}
