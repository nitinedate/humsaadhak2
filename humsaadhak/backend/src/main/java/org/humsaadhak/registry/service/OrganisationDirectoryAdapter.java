package org.humsaadhak.registry.service;

import org.humsaadhak.registry.repository.OrganisationRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.humsaadhak.documents.api.OrganisationDirectory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Registry's implementation of the names lookup that the documents module declares (dependency inversion). */
@Component
public class OrganisationDirectoryAdapter implements OrganisationDirectory {
    private final OrganisationRepository orgs;

    public OrganisationDirectoryAdapter(OrganisationRepository orgs) { this.orgs = orgs; }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Collection<Long> organisationIds) {
        Map<Long, String> names = new HashMap<>();
        if (organisationIds.isEmpty()) return names;
        orgs.findAllById(Set.copyOf(organisationIds)).forEach(o -> names.put(o.getId(), o.getName()));
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> idsNamed(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return orgs.findByNameContainingIgnoreCase(text.strip(), PageRequest.of(0, 1000)).map(o -> o.getId())
                .stream().collect(java.util.stream.Collectors.toSet());
    }
}
