package org.humsaadhak.shared.security;

import java.util.Set;
import org.humsaadhak.shared.model.AuditTarget;

/**
 * Implemented by each module for the records it owns: which organisations' people may see a record's audit trail
 * (the donor of a purpose, the school of a cause, both sides of a share request). Staff may always see it.
 */
public interface AuditOwnership {
    Set<AuditTarget> supports();

    /** Organisations that own or share the record; empty when it no longer exists or belongs to staff only. */
    Set<Long> organisations(AuditTarget type, String id);
}
