package org.humsaadhak.registry.entity;

import jakarta.persistence.*;
import org.humsaadhak.shared.entity.AuditedEntity;

/** Any number of contacts per organisation, replacing signatory_1 / signatory_2 columns. */
@Entity
@Table(name = "org_contact")
public class OrgContact extends AuditedEntity {
    public enum ContactRole { PRIMARY, SECONDARY, SIGNATORY }

    @Column(name = "org_id", nullable = false) private long orgId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ContactRole role;
    @Column(nullable = false) private String name;
    private String designation;
    private String email;
    private String phone;

    protected OrgContact() {}

    public OrgContact(long orgId, ContactRole role, String name, String designation, String email, String phone) {
        this.orgId = orgId; this.role = role; this.name = name; this.designation = designation; this.email = email; this.phone = phone;
    }

    public long getOrgId() { return orgId; }
    public ContactRole getRole() { return role; }
    public String getName() { return name; }
    public String getDesignation() { return designation; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}
