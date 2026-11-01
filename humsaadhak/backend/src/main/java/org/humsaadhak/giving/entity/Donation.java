package org.humsaadhak.giving.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "donation")
@Audited
public class Donation extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(name = "donor_org_id", nullable = false) private long donorOrgId;
    @Column(name = "need_id", nullable = false) private long needId;
    @Column(name = "purpose_id") private Long purposeId;
    @Column(name = "match_id") private Long matchId;
    @Column(name = "pledged_amount", precision = 14, scale = 2) private BigDecimal pledgedAmount;
    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2) private BigDecimal paidAmount;
    @Column(nullable = false, length = 3) private String currency = "INR";
    @Column(name = "payment_ref", nullable = false) private String paymentRef;
    @Column(name = "paid_on", nullable = false) private LocalDate paidOn;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(nullable = false) private boolean confirmed;
    protected Donation() {}

    public Donation(long donorOrgId, long needId, Long purposeId, Long matchId, BigDecimal pledgedAmount, BigDecimal paidAmount,
                    String paymentRef, LocalDate paidOn, String idempotencyKey) {
        this.donorOrgId = donorOrgId; this.needId = needId; this.purposeId = purposeId; this.matchId = matchId;
        this.pledgedAmount = pledgedAmount; this.paidAmount = paidAmount; this.paymentRef = paymentRef; this.paidOn = paidOn;
        this.idempotencyKey = idempotencyKey;
    }

    // ---- accessors
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public long getDonorOrgId() { return donorOrgId; }
    public void setDonorOrgId(long donorOrgId) { this.donorOrgId = donorOrgId; }
    public long getNeedId() { return needId; }
    public void setNeedId(long needId) { this.needId = needId; }
    public Long getPurposeId() { return purposeId; }
    public void setPurposeId(Long purposeId) { this.purposeId = purposeId; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public BigDecimal getPledgedAmount() { return pledgedAmount; }
    public void setPledgedAmount(BigDecimal pledgedAmount) { this.pledgedAmount = pledgedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
