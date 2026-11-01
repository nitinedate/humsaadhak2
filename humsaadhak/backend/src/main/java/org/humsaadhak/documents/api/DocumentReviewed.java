package org.humsaadhak.documents.api;

/** A document was reviewed or rejected; registry recomputes the owner's verification. */
public record DocumentReviewed(long documentId, long ownerOrgId, long documentTypeId, String state) {}
