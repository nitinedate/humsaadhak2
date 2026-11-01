package org.humsaadhak.registry.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "bank_account")
public class BankAccount extends AuditedEntity {
    @Column(name = "org_id", nullable = false) private long orgId;
    @Column(name = "account_name", nullable = false) private String accountName;
    /** Encrypted at rest (AES-GCM); only ever leaves the API masked. */
    @Convert(converter = org.humsaadhak.shared.entity.EncryptedStringConverter.class)
    @Column(name = "account_number", nullable = false, length = 200) private String accountNumber;
    @Column(nullable = false) private String ifsc;
    @Column(name = "bank_name") private String bankName;
    private String branch;

    protected BankAccount() {}

    public BankAccount(long orgId, String accountName, String accountNumber, String ifsc, String bankName, String branch) {
        this.orgId = orgId; this.accountName = accountName; this.accountNumber = accountNumber;
        this.ifsc = ifsc.toUpperCase(); this.bankName = bankName; this.branch = branch;
    }

    public long getOrgId() { return orgId; }
    public String getAccountName() { return accountName; }
    public String getIfsc() { return ifsc; }
    public String getBankName() { return bankName; }

    /** Shown everywhere except to staff with the reveal permission (S-series: PAN and bank numbers masked). */
    public String maskedNumber() {
        String n = accountNumber == null ? "" : accountNumber;
        return n.length() <= 4 ? "••••" : "••••" + n.substring(n.length() - 4);
    }
}
