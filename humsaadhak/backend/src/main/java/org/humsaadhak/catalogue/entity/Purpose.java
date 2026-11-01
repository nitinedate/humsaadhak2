package org.humsaadhak.catalogue.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.shared.model.Periodicity;
import org.humsaadhak.shared.util.RefNo;

/** A donor's funding intent. Approval state lives in the workflow module. */
@Entity
@Table(name = "purpose")
@Audited
public class Purpose extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(name = "donor_org_id", nullable = false) private long donorOrgId;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 8000) private String description;
    @Column(nullable = false) private String sector;
    @Column(name = "commitment_amount", precision = 14, scale = 2) private BigDecimal commitmentAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Periodicity periodicity = Periodicity.ONE_TIME;
    @Column(nullable = false) private boolean approved;
    @Column(nullable = false) private boolean matched;
    @Column(nullable = false) private boolean completed;

    protected Purpose() {}

    public Purpose(long donorOrgId, String name, String description, String sector, BigDecimal commitmentAmount, Periodicity periodicity) {
        this.donorOrgId = donorOrgId;
        update(name, description, sector, commitmentAmount, periodicity);
    }

    /** @return true when the change is material and needs re-approval. */
    public boolean update(String name, String description, String sector, BigDecimal amount, Periodicity periodicity) {
        boolean material = this.name != null && (!name.equals(this.name) || !description.equals(this.description)
                || !sector.equals(this.sector) || (amount != null && amount.compareTo(commitmentAmount == null ? BigDecimal.ZERO : commitmentAmount) != 0)
                || periodicity != this.periodicity);
        this.name = name; this.description = description; this.sector = sector;
        this.commitmentAmount = amount; this.periodicity = periodicity;
        return material;
    }

    public void assignRefNo() { if (refNo == null) refNo = RefNo.of(RefNo.PURPOSE, getId()); }
    /** The day the donor's commitment was first approved (the reports' "Commitment Date"); kept on later re-approvals. */
    @Column(name = "commitment_date") private java.time.LocalDate commitmentDate;

    public void setApproved(boolean approved, java.time.LocalDate today) {
        this.approved = approved;
        if (approved && commitmentDate == null) commitmentDate = today;
    }
    public void setApproved(boolean approved) { setApproved(approved, java.time.LocalDate.now()); }
    public java.time.LocalDate getCommitmentDate() { return commitmentDate; }
    public void markMatched() { this.matched = true; }
    public void markCompleted() { this.completed = true; }

    public String getRefNo() { return refNo; }
    public long getDonorOrgId() { return donorOrgId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSector() { return sector; }
    public BigDecimal getCommitmentAmount() { return commitmentAmount; }
    public Periodicity getPeriodicity() { return periodicity; }
    public boolean isApproved() { return approved; }
    public boolean isMatched() { return matched; }
    public boolean isCompleted() { return completed; }
}
