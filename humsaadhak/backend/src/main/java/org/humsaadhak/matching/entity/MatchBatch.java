package org.humsaadhak.matching.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "match_batch")
public class MatchBatch extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(name = "purpose_id", nullable = false) private long purposeId;
    @Column(name = "confirmed_by", nullable = false) private long confirmedBy;
    protected MatchBatch() {}
    public MatchBatch(long purposeId, long confirmedBy) { this.purposeId = purposeId; this.confirmedBy = confirmedBy; }

    // ---- accessors
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public long getPurposeId() { return purposeId; }
    public void setPurposeId(long purposeId) { this.purposeId = purposeId; }
    public long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(long confirmedBy) { this.confirmedBy = confirmedBy; }
}
