package org.humsaadhak.registry.util;

import java.util.Map;
import java.util.Set;

/**
 * Fixes D7: verified ⇔ every mandatory document type FOR THIS ORGANISATION TYPE is reviewed and not rejected.
 * Pure function, unit-tested; the mandatory sets come from document_type_rule.
 */
public final class MandatoryDocumentPolicy {
    public enum DocState { PENDING, REVIEWED, REJECTED }

    private MandatoryDocumentPolicy() {}

    /**
     * @param mandatoryTypeIds mandatory document type ids for the organisation's type
     * @param latestStateByType state of the latest version of each uploaded type
     */
    public static boolean isVerified(Set<Long> mandatoryTypeIds, Map<Long, DocState> latestStateByType) {
        if (mandatoryTypeIds.isEmpty()) return false;
        for (Long typeId : mandatoryTypeIds) {
            if (latestStateByType.get(typeId) != DocState.REVIEWED) return false;
        }
        return true;
    }
}
