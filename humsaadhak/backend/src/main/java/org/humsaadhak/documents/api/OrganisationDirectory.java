package org.humsaadhak.documents.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Organisation names for screens owned by documents (document requests, review queue). Implemented by the registry
 * module, which already depends on documents: declaring the interface here keeps the dependency one-way (no cycle).
 */
public interface OrganisationDirectory {
    Map<Long, String> namesOf(Collection<Long> organisationIds);

    /** Ids of organisations whose name contains the text (any case), for filtering lists by organisation. */
    Set<Long> idsNamed(String text);
}
