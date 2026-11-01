package org.humsaadhak.documents.api;

import java.util.Map;
import java.util.Set;

public interface DocumentStates {
    /** Mandatory document type ids for an organisation type name (DONOR_CORPORATE, SCHOOL, …). */
    Set<Long> mandatoryTypeIds(String organisationType);
    /** For each document type the organisation uploaded: state of its latest version (PENDING, REVIEWED, REJECTED). */
    Map<Long, String> latestStateByType(long orgId);
}
