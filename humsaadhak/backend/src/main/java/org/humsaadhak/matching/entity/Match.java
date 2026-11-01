package org.humsaadhak.matching.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "match", uniqueConstraints = @UniqueConstraint(columnNames = {"purpose_id", "need_id"}))
public class Match extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(name = "batch_id", nullable = false) private long batchId;
    @Column(name = "candidate_id", nullable = false) private long candidateId;
    @Column(name = "purpose_id", nullable = false) private long purposeId;
    @Column(name = "need_id", nullable = false) private long needId;
    @Column(name = "donor_org_id", nullable = false) private long donorOrgId;
    @Column(nullable = false) private int priority;
    protected Match() {}
    public Match(long batchId, long candidateId, long purposeId, long needId, long donorOrgId, int priority) {
        this.batchId = batchId; this.candidateId = candidateId; this.purposeId = purposeId; this.needId = needId;
        this.donorOrgId = donorOrgId; this.priority = priority;
    }

    // ---- accessors
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public long getBatchId() { return batchId; }
    public void setBatchId(long batchId) { this.batchId = batchId; }
    public long getCandidateId() { return candidateId; }
    public void setCandidateId(long candidateId) { this.candidateId = candidateId; }
    public long getPurposeId() { return purposeId; }
    public void setPurposeId(long purposeId) { this.purposeId = purposeId; }
    public long getNeedId() { return needId; }
    public void setNeedId(long needId) { this.needId = needId; }
    public long getDonorOrgId() { return donorOrgId; }
    public void setDonorOrgId(long donorOrgId) { this.donorOrgId = donorOrgId; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
