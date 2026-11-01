package org.humsaadhak.giving.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.shared.model.Periodicity;

@Entity
@Table(name = "recurring_plan", uniqueConstraints = @UniqueConstraint(columnNames = {"donor_org_id", "need_id"}))
public class RecurringPlan extends AuditedEntity {
    @Column(name = "donor_org_id", nullable = false) private long donorOrgId;
    @Column(name = "need_id", nullable = false) private long needId;
    @Column(name = "purpose_id") private Long purposeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Periodicity periodicity;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private boolean active = true;
    protected RecurringPlan() {}
    public RecurringPlan(long donorOrgId, long needId, Long purposeId, Periodicity p, BigDecimal amount) {
        this.donorOrgId = donorOrgId; this.needId = needId; this.purposeId = purposeId; this.periodicity = p; this.amount = amount;
    }

    // ---- accessors
    public long getDonorOrgId() { return donorOrgId; }
    public void setDonorOrgId(long donorOrgId) { this.donorOrgId = donorOrgId; }
    public long getNeedId() { return needId; }
    public void setNeedId(long needId) { this.needId = needId; }
    public Long getPurposeId() { return purposeId; }
    public void setPurposeId(Long purposeId) { this.purposeId = purposeId; }
    public Periodicity getPeriodicity() { return periodicity; }
    public void setPeriodicity(Periodicity periodicity) { this.periodicity = periodicity; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
