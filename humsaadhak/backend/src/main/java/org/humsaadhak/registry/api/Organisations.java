package org.humsaadhak.registry.api;

import java.util.Optional;

public interface Organisations {
    record Joined(long orgId, boolean existing) {}
    record Summary(long id, String refNo, String name, OrganisationType type, String city, String state, boolean verified) {}

    /** Joins an existing verified organisation of the same name and type, or creates a new one. */
    Joined findOrCreateForRegistration(String name, OrganisationType type, String city, String state);
    Optional<Summary> summary(long orgId);
    /** For imports: "BN100045" -> organisation id. */
    Optional<Long> idByRefNo(String refNo);
}
