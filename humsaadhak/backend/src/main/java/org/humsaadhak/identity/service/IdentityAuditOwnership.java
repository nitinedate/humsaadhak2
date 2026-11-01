package org.humsaadhak.identity.service;

import java.util.Set;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditOwnership;
import org.springframework.stereotype.Component;

/** A person's trail (sign-ins, role changes, settings) belongs to their organisation; staff accounts to staff only. */
@Component
public class IdentityAuditOwnership implements AuditOwnership {
    private final AppUserRepository users;

    public IdentityAuditOwnership(AppUserRepository users) { this.users = users; }

    @Override public Set<AuditTarget> supports() { return Set.of(AuditTarget.USER); }

    @Override
    public Set<Long> organisations(AuditTarget type, String id) {
        return users.findById(Long.parseLong(id)).map(u -> u.getOrgId() == null ? Set.<Long>of() : Set.of(u.getOrgId())).orElse(Set.of());
    }
}
