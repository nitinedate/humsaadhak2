package org.humsaadhak.matching.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "match_candidate", uniqueConstraints = @UniqueConstraint(columnNames = {"purpose_id", "need_id"}))
@Audited
public class MatchCandidate extends AuditedEntity {
    public enum Decision { PENDING, APPROVED, REJECTED }

    @Column(name = "run_id", nullable = false) private long runId;
    @Column(name = "purpose_id", nullable = false) private long purposeId;
    @Column(name = "need_id", nullable = false) private long needId;
    @Column(name = "retrieval_score") private Double retrievalScore;
    @Column(name = "rerank_score") private Double rerankScore;
    @Column(name = "final_score", nullable = false) private int finalScore;
    @Column(name = "engine_rank", nullable = false) private int engineRank;
    @Column(name = "model_version", nullable = false) private String modelVersion;
    @Column(name = "explanation", length = 4000) private String explanationJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Decision decision = Decision.PENDING;
    @Column(length = 2000) private String remark;
    @Column(name = "priority") private Integer priority;

    protected MatchCandidate() {}
    public MatchCandidate(long runId, long purposeId, long needId, Double retrieval, Double rerank, int finalScore, int engineRank,
                   String modelVersion, String explanationJson) {
        this.runId = runId; this.purposeId = purposeId; this.needId = needId; this.retrievalScore = retrieval;
        this.rerankScore = rerank; this.finalScore = finalScore; this.engineRank = engineRank;
        this.modelVersion = modelVersion; this.explanationJson = explanationJson;
    }

    // ---- accessors
    /** A later engine run's verdict on a candidate still waiting for review (RerunPlan: RESCORE or HIDE). */
    public void rescore(long runId, Double retrieval, Double rerank, int finalScore, int engineRank, String modelVersion, String explanationJson) {
        this.runId = runId; this.retrievalScore = retrieval; this.rerankScore = rerank; this.finalScore = finalScore;
        this.engineRank = engineRank; this.modelVersion = modelVersion; this.explanationJson = explanationJson;
    }
    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }
    public long getPurposeId() { return purposeId; }
    public void setPurposeId(long purposeId) { this.purposeId = purposeId; }
    public long getNeedId() { return needId; }
    public void setNeedId(long needId) { this.needId = needId; }
    public Double getRetrievalScore() { return retrievalScore; }
    public void setRetrievalScore(Double retrievalScore) { this.retrievalScore = retrievalScore; }
    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }
    public int getFinalScore() { return finalScore; }
    public void setFinalScore(int finalScore) { this.finalScore = finalScore; }
    public int getEngineRank() { return engineRank; }
    public void setEngineRank(int engineRank) { this.engineRank = engineRank; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getExplanationJson() { return explanationJson; }
    public void setExplanationJson(String explanationJson) { this.explanationJson = explanationJson; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
