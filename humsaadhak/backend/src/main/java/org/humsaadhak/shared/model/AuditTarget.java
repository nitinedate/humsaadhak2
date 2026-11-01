package org.humsaadhak.shared.model;

/** The kinds of record an audited action can act on. Ids are kept as text because roles, permissions and sectors use codes. */
public enum AuditTarget {
    NONE, USER, ORGANISATION, PURPOSE, NEED, DOCUMENT, SHARE_REQUEST, DONATION, MATCH_RUN, IMPORT_JOB,
    ROLE, PERMISSION, SECTOR, DOCUMENT_TYPE
}
