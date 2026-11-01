package org.humsaadhak.identity.api;

public record UserRegistered(long userId, Long orgId, boolean joinedExistingOrganisation) {}
