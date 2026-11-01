package org.humsaadhak.identity.api;

public record PasswordResetRequested(long userId, String email, String fullName) {}
