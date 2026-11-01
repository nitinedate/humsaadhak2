package org.humsaadhak.identity.api;

/** Creates a fresh single-use link at the moment an email is composed, so no token ever sits in an event table. */
public interface InvitationLinks {
    String issueInvitationLink(long userId);
    String issuePasswordResetLink(long userId);
}
