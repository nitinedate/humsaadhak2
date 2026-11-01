package org.humsaadhak.identity.api;

import java.util.List;

/** Active people of an organisation, for notifications. */
public interface UserDirectory {
    record Recipient(String email, String fullName) {}
    List<Recipient> activeUsersOfOrg(long orgId);
}
