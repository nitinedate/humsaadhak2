package org.humsaadhak.identity.api;

/** A manager approved a new user; notifications sends the invitation. Ids only, never secrets. */
public record UserActivated(long userId, String email, String fullName) {}
