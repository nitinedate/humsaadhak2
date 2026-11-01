package org.humsaadhak.registry.service;

import org.humsaadhak.registry.util.MandatoryDocumentPolicy;
import org.humsaadhak.registry.entity.Organisation;
import org.humsaadhak.registry.repository.OrganisationRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.humsaadhak.documents.api.DocumentReviewed;
import org.humsaadhak.documents.api.MandatoryRulesChanged;
import org.humsaadhak.documents.api.DocumentStates;
import org.humsaadhak.registry.api.OrganisationType;
import org.humsaadhak.registry.api.OrganisationVerified;
import org.humsaadhak.registry.api.Organisations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganisationService implements Organisations {
    private final OrganisationRepository orgs;
    private final DocumentStates documentStates;
    private final ApplicationEventPublisher events;

    public OrganisationService(OrganisationRepository orgs, DocumentStates documentStates, ApplicationEventPublisher events) {
        this.orgs = orgs; this.documentStates = documentStates; this.events = events;
    }

    @Override
    @Transactional
    public Joined findOrCreateForRegistration(String name, OrganisationType type, String city, String state) {
        if (type != OrganisationType.DONOR_INDIVIDUAL) {
            Optional<Organisation> existing = orgs.findFirstByNameIgnoreCaseAndTypeAndVerifiedTrue(name.trim(), type);
            if (existing.isPresent()) return new Joined(existing.get().getId(), true);
        }
        Organisation org = orgs.save(new Organisation(name, type, city, state));
        org.assignRefNo();
        return new Joined(org.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Summary> summary(long orgId) {
        return orgs.findById(orgId).map(o -> new Summary(o.getId(), o.getRefNo(), o.getName(), o.getType(),
                o.getCity(), o.getState(), o.isVerified()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> idByRefNo(String refNo) {
        return refNo == null ? Optional.empty() : orgs.findByRefNo(refNo.trim().toUpperCase()).map(Organisation::getId);
    }

    /** Recompute verification whenever one of the organisation's documents is reviewed or rejected. */
    @ApplicationModuleListener
    public void on(DocumentReviewed e) {
        orgs.findById(e.ownerOrgId()).ifPresent(this::recomputeVerification);
    }

    /** An administrator changed which documents an organisation type must provide: re-check every such organisation. */
    @ApplicationModuleListener
    public void on(MandatoryRulesChanged e) {
        e.organisationTypes().forEach(type -> orgs.findByType(OrganisationType.valueOf(type), org.springframework.data.domain.Pageable.unpaged())
                .forEach(this::recomputeVerification));
    }

    private void recomputeVerification(Organisation org) {
        Set<Long> mandatory = documentStates.mandatoryTypeIds(org.getType().name());
        Map<Long, MandatoryDocumentPolicy.DocState> latest = new HashMap<>();
        documentStates.latestStateByType(org.getId())
                .forEach((typeId, st) -> latest.put(typeId, MandatoryDocumentPolicy.DocState.valueOf(st)));
        boolean verified = MandatoryDocumentPolicy.isVerified(mandatory, latest);
        if (verified != org.isVerified()) {
            org.setVerified(verified);
            events.publishEvent(new OrganisationVerified(org.getId(), verified));
        }
    }
}
