package org.humsaadhak.documents.dto;

import java.time.Instant;

/** A document an organisation shares with a donor: the current, HumSaadhak-approved version of a shareable type. */
public record SharedDocumentRow(long id, String typeName, String name, int version, Instant approvedAt) {}
