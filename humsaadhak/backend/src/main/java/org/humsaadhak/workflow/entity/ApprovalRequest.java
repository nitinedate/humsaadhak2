package org.humsaadhak.workflow.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.humsaadhak.workflow.api.SubjectType;

@Entity
@Table(name = "approval_request", uniqueConstraints = @UniqueConstraint(columnNames = {"subject_type", "subject_id"}))
public class ApprovalRequest extends AuditedEntity {
    @Enumerated(EnumType.STRING) @Column(name = "subject_type", nullable = false) private SubjectType subjectType;
    @Column(name = "subject_id", nullable = false) private long subjectId;
    @Column(name = "maker_id", nullable = false) private long makerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ApprovalStatus status = ApprovalStatus.DRAFT;
    @Column(nullable = false) private int revision = 1;
    @Column(name = "last_comment", length = 2000) private String lastComment;
    @Column(name = "subject_title", length = 300) private String subjectTitle;

    protected ApprovalRequest() {}

    public ApprovalRequest(SubjectType type, long subjectId, long makerId, String title) {
        this.subjectType = type; this.subjectId = subjectId; this.makerId = makerId; this.subjectTitle = title;
    }

    public void moveTo(ApprovalStatus next, String comment) { this.status = next; this.lastComment = comment; }
    public void newRevision(long maker) { this.revision++; this.makerId = maker; }

    public SubjectType getSubjectType() { return subjectType; }
    public long getSubjectId() { return subjectId; }
    public long getMakerId() { return makerId; }
    public ApprovalStatus getStatus() { return status; }
    public int getRevision() { return revision; }
    public String getLastComment() { return lastComment; }
    public String getSubjectTitle() { return subjectTitle; }
}
