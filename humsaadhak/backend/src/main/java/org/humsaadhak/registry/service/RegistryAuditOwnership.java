package org.humsaadhak.registry.service;

import java.util.Set;
import org.humsaadhak.registry.repository.OrganisationRepository;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditOwnership;
import org.springframework.stereotype.Component;

/** An organisation's trail (profile, contacts, bank accounts) belongs to the organisation itself. */
@Component
public class RegistryAuditOwnership implements AuditOwnership {
    private final OrganisationRepository organisations;

    public RegistryAuditOwnership(OrganisationRepository organisations) { this.organisations = organisations; }

    @Override public Set<AuditTarget> supports() { return Set.of(AuditTarget.ORGANISATION); }

    @Override
    public Set<Long> organisations(AuditTarget type, String id) {
        long key = Long.parseLong(id);
        return organisations.existsById(key) ? Set.of(key) : Set.of();
    }
}
