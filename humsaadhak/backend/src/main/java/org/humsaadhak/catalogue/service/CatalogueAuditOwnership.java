package org.humsaadhak.catalogue.service;

import java.util.Set;
import org.humsaadhak.catalogue.entity.Need;
import org.humsaadhak.catalogue.entity.Purpose;
import org.humsaadhak.catalogue.repository.NeedRepository;
import org.humsaadhak.catalogue.repository.PurposeRepository;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditOwnership;
import org.springframework.stereotype.Component;

/** A purpose's trail belongs to its donor; a cause's or project's to its school or NGO. */
@Component
public class CatalogueAuditOwnership implements AuditOwnership {
    private final PurposeRepository purposes;
    private final NeedRepository needs;

    public CatalogueAuditOwnership(PurposeRepository purposes, NeedRepository needs) { this.purposes = purposes; this.needs = needs; }

    @Override public Set<AuditTarget> supports() { return Set.of(AuditTarget.PURPOSE, AuditTarget.NEED); }

    @Override
    public Set<Long> organisations(AuditTarget type, String id) {
        long key = Long.parseLong(id);
        return type == AuditTarget.PURPOSE
                ? purposes.findById(key).map(Purpose::getDonorOrgId).map(Set::of).orElse(Set.of())
                : needs.findById(key).map(Need::getOrgId).map(Set::of).orElse(Set.of());
    }
}
