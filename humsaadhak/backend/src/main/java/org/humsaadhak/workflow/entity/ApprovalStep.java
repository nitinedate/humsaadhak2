package org.humsaadhak.workflow.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.workflow.api.ApprovalStatus;

/** One row per transition: who moved what, from where to where, and why. */
@Entity
@Table(name = "approval_step")
public class ApprovalStep extends AuditedEntity {
    @Column(name = "request_id", nullable = false) private long requestId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private ApprovalStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false) private ApprovalStatus toStatus;
    @Column(name = "actor_id", nullable = false) private long actorId;
    @Column(length = 2000) private String comment;

    protected ApprovalStep() {}

    public ApprovalStep(long requestId, ApprovalStatus from, ApprovalStatus to, long actorId, String comment) {
        this.requestId = requestId; this.fromStatus = from; this.toStatus = to; this.actorId = actorId; this.comment = comment;
    }

    public ApprovalStatus getFromStatus() { return fromStatus; }
    public ApprovalStatus getToStatus() { return toStatus; }
    public long getActorId() { return actorId; }
    public String getComment() { return comment; }
}
