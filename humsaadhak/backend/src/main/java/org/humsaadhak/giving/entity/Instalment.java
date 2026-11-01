package org.humsaadhak.giving.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "instalment", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "due_date"}))
public class Instalment extends AuditedEntity {
    public enum Status { DUE, PAID, SKIPPED }
    @Column(name = "plan_id", nullable = false) private long planId;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DUE;
    @Column(name = "reminder_sent_at") private Instant reminderSentAt;
    @Column(name = "donation_id") private Long donationId;
    protected Instalment() {}
    public Instalment(long planId, LocalDate due, BigDecimal amount) { this.planId = planId; this.dueDate = due; this.amount = amount; }

    // ---- accessors
    public long getPlanId() { return planId; }
    public void setPlanId(long planId) { this.planId = planId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Instant reminderSentAt) { this.reminderSentAt = reminderSentAt; }
    public Long getDonationId() { return donationId; }
    public void setDonationId(Long donationId) { this.donationId = donationId; }
}
