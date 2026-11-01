package org.humsaadhak.audit.util;

import java.util.Set;

/** Who may see what in the audit trail (pure, unit-tested). */
public final class AuditVisibility {
    private AuditVisibility() {}

    /** Staff see every record's trail; others only the trails of records their organisation owns or shares. */
    public static boolean canSeeTrail(boolean staff, Long myOrg, Set<Long> owners) {
        return staff || (myOrg != null && owners.contains(myOrg));
    }

    /** Refused and failed attempts, IP addresses and browsers are shown only with the audit.view permission. */
    public static boolean seesEverything(boolean hasAuditView) { return hasAuditView; }
}
