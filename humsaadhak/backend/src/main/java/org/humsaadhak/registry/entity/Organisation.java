package org.humsaadhak.registry.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.humsaadhak.registry.api.OrganisationType;
import org.humsaadhak.shared.entity.AuditedEntity;
import org.humsaadhak.shared.util.RefNo;

@Entity
@Table(name = "organisation")
@Audited
public class Organisation extends AuditedEntity {
    @Column(name = "ref_no", unique = true) private String refNo;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrganisationType type;
    private String city;
    private String state;
    @Column(name = "pan_masked") private String panMasked;
    @Column(nullable = false) private boolean verified;
    @Column(name = "legacy_id") private Long legacyId;

    protected Organisation() {}

    public Organisation(String name, OrganisationType type, String city, String state) {
        this.name = name.trim(); this.type = type; this.city = city; this.state = state;
    }

    public void assignRefNo() { if (refNo == null) refNo = RefNo.of(type.refPrefix(), getId()); }
    public void setVerified(boolean verified) { this.verified = verified; }
    public void updateProfile(String name, String city, String state) { this.name = name.trim(); this.city = city; this.state = state; }

    public String getRefNo() { return refNo; }
    public String getName() { return name; }
    public OrganisationType getType() { return type; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public boolean isVerified() { return verified; }
}
