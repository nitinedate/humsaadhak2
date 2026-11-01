package org.humsaadhak.catalogue.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;
import org.humsaadhak.catalogue.api.NeedKind;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.shared.model.Periodicity;
import org.humsaadhak.shared.util.RefNo;

/** A school cause or an NGO project. Fixes D8: completed only when the target is raised. */
@Entity
@Table(name = "need")
@Audited
public class Need extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(name = "org_id", nullable = false) private long orgId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NeedKind kind;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 8000) private String description;
    @Column(nullable = false) private String sector;
    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2) private BigDecimal targetAmount;
    @Column(name = "raised_amount", nullable = false, precision = 14, scale = 2) private BigDecimal raisedAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Periodicity periodicity = Periodicity.ONE_TIME;
    @Column(nullable = false) private boolean approved;
    @Column(nullable = false) private boolean completed;

    protected Need() {}

    public Need(long orgId, NeedKind kind, String name, String description, String sector, BigDecimal target, Periodicity periodicity) {
        this.orgId = orgId; this.kind = kind;
        update(name, description, sector, target, periodicity);
    }

    public boolean update(String name, String description, String sector, BigDecimal target, Periodicity periodicity) {
        if (target == null || target.signum() <= 0) throw new IllegalArgumentException("Target must be positive");
        boolean material = this.name != null && (!name.equals(this.name) || !description.equals(this.description)
                || !sector.equals(this.sector) || target.compareTo(targetAmount) != 0 || periodicity != this.periodicity);
        this.name = name; this.description = description; this.sector = sector;
        this.targetAmount = target; this.periodicity = periodicity;
        return material;
    }

    /** Adds a payment; the need completes when the money raised reaches the target. */
    public void addPayment(BigDecimal paid) {
        raisedAmount = raisedAmount.add(paid);
        if (raisedAmount.compareTo(targetAmount) >= 0) completed = true;
    }

    public void assignRefNo() { if (refNo == null) refNo = RefNo.of(kind.refPrefix(), getId()); }
    /** Approval date shown and filtered on by the beneficiary reports; cleared when an edit sends it back for approval. */
    @Column(name = "approved_at") private java.time.Instant approvedAt;

    public void setApproved(boolean approved, java.time.Instant at) { this.approved = approved; this.approvedAt = approved ? at : null; }
    public void setApproved(boolean approved) { setApproved(approved, java.time.Instant.now()); }
    public java.time.Instant getApprovedAt() { return approvedAt; }

    public String getRefNo() { return refNo; }
    public long getOrgId() { return orgId; }
    public NeedKind getKind() { return kind; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSector() { return sector; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getRaisedAmount() { return raisedAmount; }
    public Periodicity getPeriodicity() { return periodicity; }
    public boolean isApproved() { return approved; }
    public boolean isCompleted() { return completed; }
}
