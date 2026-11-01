package org.humsaadhak.documents.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

/** A donor asks to see a matched beneficiary's documents; the beneficiary decides. */
@Entity
@Table(name = "share_request", uniqueConstraints = @UniqueConstraint(columnNames = {"need_id", "donor_org_id"}))
public class ShareRequest extends AuditedEntity {
    public enum Status { REQUESTED, APPROVED, REJECTED }

    @Column(name = "need_id", nullable = false) private long needId;
    @Column(name = "owner_org_id", nullable = false) private long ownerOrgId;
    @Column(name = "donor_org_id", nullable = false) private long donorOrgId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.REQUESTED;
    @Column(length = 2000) private String reason;

    protected ShareRequest() {}

    public ShareRequest(long needId, long ownerOrgId, long donorOrgId) {
        this.needId = needId; this.ownerOrgId = ownerOrgId; this.donorOrgId = donorOrgId;
    }

    public void decide(boolean approve, String reason) {
        if (status != Status.REQUESTED) throw new org.humsaadhak.shared.exception.DomainException("already-decided", "This request was already answered.");
        this.status = approve ? Status.APPROVED : Status.REJECTED;
        this.reason = reason;
    }

    public long getNeedId() { return needId; }
    public long getOwnerOrgId() { return ownerOrgId; }
    public long getDonorOrgId() { return donorOrgId; }
    public Status getStatus() { return status; }
}
