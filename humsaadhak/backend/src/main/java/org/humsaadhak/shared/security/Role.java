package org.humsaadhak.shared.security;

import java.util.EnumSet;
import java.util.Set;
import static org.humsaadhak.shared.security.Permission.*;

/**
 * The account type: staff, individual or corporate donor, school, NGO or guest. It decides whose data a person works
 * with and which dashboard they see. What they may do comes from the permissions of their assigned role, which super
 * admins manage (app_role / app_role_permission); the sets below are the starting permissions of the system roles,
 * seeded by V6 (RoleSeedTest checks the two stay the same).
 */
public enum Role {
    SUPER_ADMIN(EnumSet.of(ADMIN_USERS, ADMIN_ROLES, IMPORT_RUN, DASHBOARD_PLATFORM, REFERENCE_MANAGE, AUDIT_VIEW)),
    MANAGER(EnumSet.of(USER_APPROVE, ORG_APPROVE, RECORD_APPROVE, DOCUMENT_REVIEW, MATCH_RUN, MATCH_DECIDE, DASHBOARD_PLATFORM, AUDIT_VIEW)),
    ASSOCIATE(EnumSet.of(PURPOSE_EDIT, NEED_EDIT, DOCUMENT_UPLOAD, MATCH_DECIDE, DONATION_RECORD, IMPORT_RUN, DASHBOARD_PLATFORM)),
    DONOR_INDIVIDUAL(EnumSet.of(PURPOSE_EDIT, DOCUMENT_UPLOAD, SHARE_REQUEST, DONATION_RECORD)),
    DONOR_CORPORATE(EnumSet.of(PURPOSE_EDIT, DOCUMENT_UPLOAD, SHARE_REQUEST, DONATION_RECORD)),
    SCHOOL(EnumSet.of(NEED_EDIT, DOCUMENT_UPLOAD, SHARE_DECIDE)),
    NGO(EnumSet.of(NEED_EDIT, DOCUMENT_UPLOAD, SHARE_DECIDE)),
    GUEST(EnumSet.noneOf(Permission.class));

    private final Set<Permission> permissions;
    Role(Set<Permission> permissions) { this.permissions = permissions; }

    public Set<Permission> defaultPermissions() { return permissions; }
    public boolean isStaff() { return this == SUPER_ADMIN || this == MANAGER || this == ASSOCIATE; }
    public boolean isDonor() { return this == DONOR_INDIVIDUAL || this == DONOR_CORPORATE; }
    public boolean isBeneficiary() { return this == SCHOOL || this == NGO; }
}
