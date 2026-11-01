package org.humsaadhak.shared.security;

/** Fine-grained permissions checked by code. Roles are bundles of these (see Role). */
public enum Permission {
    ADMIN_USERS("admin.users"),
    /** Create roles and permissions and decide which permissions each role grants. */
    ADMIN_ROLES("admin.roles"),
    USER_APPROVE("user.approve"),
    ORG_APPROVE("org.approve"),
    PURPOSE_EDIT("purpose.edit"),
    NEED_EDIT("need.edit"),
    RECORD_APPROVE("record.approve"),
    DOCUMENT_UPLOAD("document.upload"),
    DOCUMENT_REVIEW("document.review"),
    MATCH_RUN("match.run"),
    MATCH_DECIDE("match.decide"),
    SHARE_REQUEST("share.request"),
    SHARE_DECIDE("share.decide"),
    DONATION_RECORD("donation.record"),
    IMPORT_RUN("import.run"),
    DASHBOARD_PLATFORM("dashboard.platform"),
    /** Sectors, document types and which documents each organisation type must provide. */
    REFERENCE_MANAGE("reference.manage"),
    /** The full audit log: every action, including refused and failed attempts, with IP addresses. */
    AUDIT_VIEW("audit.view");

    private final String code;
    Permission(String code) { this.code = code; }

    public String code() { return code; }
    /** The Spring Security authority string, e.g. "perm:match.run". */
    public String authority() { return "perm:" + code; }
}
