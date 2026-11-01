package org.humsaadhak.documents.util;

import java.util.HashSet;
import java.util.Set;

/** Pure: which organisation types are affected when a document type's mandatory set changes (to re-check verification). */
public final class MandatoryRuleChange {
    public static final Set<String> ORGANISATION_TYPES = Set.of("DONOR_INDIVIDUAL", "DONOR_CORPORATE", "SCHOOL", "NGO");

    private MandatoryRuleChange() {}

    public static Set<String> affected(Set<String> before, Set<String> after) {
        Set<String> changed = new HashSet<>(before);
        changed.addAll(after);
        Set<String> both = new HashSet<>(before);
        both.retainAll(after);
        changed.removeAll(both);
        return changed;
    }

    public static Set<String> validated(Set<String> types) {
        for (String t : types)
            if (!ORGANISATION_TYPES.contains(t)) throw new org.humsaadhak.shared.exception.DomainException("unknown-type", "Unknown organisation type " + t + ".");
        return types;
    }
}
