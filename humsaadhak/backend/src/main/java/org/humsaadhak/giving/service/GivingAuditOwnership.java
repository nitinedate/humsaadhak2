package org.humsaadhak.giving.service;

import java.util.HashSet;
import java.util.Set;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.giving.repository.DonationRepository;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditOwnership;
import org.springframework.stereotype.Component;

/** A donation's trail belongs to the donor and to the school or NGO it was paid to. */
@Component
public class GivingAuditOwnership implements AuditOwnership {
    private final DonationRepository donations;
    private final Catalogue catalogue;

    public GivingAuditOwnership(DonationRepository donations, Catalogue catalogue) { this.donations = donations; this.catalogue = catalogue; }

    @Override public Set<AuditTarget> supports() { return Set.of(AuditTarget.DONATION); }

    @Override
    public Set<Long> organisations(AuditTarget type, String id) {
        return donations.findById(Long.parseLong(id)).map(d -> {
            Set<Long> out = new HashSet<>();
            out.add(d.getDonorOrgId());
            catalogue.need(d.getNeedId()).ifPresent(n -> out.add(n.orgId()));
            return Set.copyOf(out);
        }).orElse(Set.of());
    }
}
