package org.humsaadhak.identity.entity;

import org.humsaadhak.identity.model.UserStatus;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.envers.Audited;
import org.humsaadhak.identity.api.Theme;
import org.humsaadhak.shared.security.Role;
import org.humsaadhak.shared.entity.AuditedEntity;

@Entity
@Table(name = "app_user")
@Audited
public class AppUser extends AuditedEntity {
    public static final int MAX_FAILED_LOGINS = 5;
    public static final long LOCK_MINUTES = 15;

    @Column(nullable = false, unique = true) private String email;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Column(name = "mobile") private String mobile;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "org_id") private Long orgId;
    /** Account type (staff, donor, school, NGO): data scope and dashboard. */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    /** Assigned role (app_role.code), whose permissions decide what the person can do. */
    @Column(name = "role_code") private String roleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserStatus status = UserStatus.PENDING;
    @Column(name = "failed_logins", nullable = false) private int failedLogins;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "totp_secret") private String totpSecret;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Theme theme = Theme.INDIGO;

    protected AppUser() {}

    public AppUser(String email, String fullName, String mobile, Role role, Long orgId) {
        this.email = email.trim().toLowerCase();
        this.fullName = fullName;
        this.mobile = mobile;
        this.role = role;
        this.roleCode = role.name();
        this.orgId = orgId;
    }

    public boolean isLocked(Instant now) { return lockedUntil != null && now.isBefore(lockedUntil); }

    public void recordFailedLogin(Instant now) {
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            lockedUntil = now.plusSeconds(LOCK_MINUTES * 60);
            failedLogins = 0;
        }
    }

    public void recordSuccessfulLogin() { failedLogins = 0; lockedUntil = null; }
    public void activate() { status = UserStatus.ACTIVE; }
    public void disable() { status = UserStatus.DISABLED; }
    public void setTheme(Theme theme) { this.theme = theme == null ? Theme.INDIGO : theme; }
    public Theme getTheme() { return theme; }
    public void reject() { status = UserStatus.REJECTED; }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    /** Assigns a role; the account type follows the role's kind. */
    public void assignRole(String roleCode, Role kind) { this.roleCode = roleCode; this.role = kind; }
    public String getRoleCode() { return roleCode != null ? roleCode : role.name(); }

    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getMobile() { return mobile; }
    public String getPasswordHash() { return passwordHash; }
    public Long getOrgId() { return orgId; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getTotpSecret() { return totpSecret; }
}
