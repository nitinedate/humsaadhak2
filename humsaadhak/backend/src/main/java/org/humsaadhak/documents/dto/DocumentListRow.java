package org.humsaadhak.documents.dto;

import java.time.Instant;

/** One document (its current version) in the staff list of all documents. reviewedBy/At are empty while it waits. */
public record DocumentListRow(long id, long ownerOrgId, String ownerName, long typeId, String typeName, String name, int version,
                              String state, String rejectionReason, String uploadedBy, Instant uploadedAt,
                              String reviewedBy, Instant reviewedAt) {}
