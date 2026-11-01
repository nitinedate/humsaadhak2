package org.humsaadhak.matching.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "match_run")
public class MatchRun extends AuditedEntity {
    @Column(name = "started_by", nullable = false) private long startedBy;
    @Column(name = "model_version") private String modelVersion;
    @Column(name = "purpose_count", nullable = false) private int purposeCount;
    @Column(name = "candidate_count", nullable = false) private int candidateCount;
    protected MatchRun() {}
    public MatchRun(long startedBy, int purposeCount) { this.startedBy = startedBy; this.purposeCount = purposeCount; }

    // ---- accessors
    public long getStartedBy() { return startedBy; }
    public void setStartedBy(long startedBy) { this.startedBy = startedBy; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public int getPurposeCount() { return purposeCount; }
    public void setPurposeCount(int purposeCount) { this.purposeCount = purposeCount; }
    public int getCandidateCount() { return candidateCount; }
    public void setCandidateCount(int candidateCount) { this.candidateCount = candidateCount; }
}
