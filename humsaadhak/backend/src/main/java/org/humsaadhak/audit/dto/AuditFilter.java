package org.humsaadhak.audit.dto;

import java.time.LocalDate;

/** Filters of the full audit log (all optional). Dates are inclusive, in India time. */
public record AuditFilter(LocalDate from, LocalDate to, String actor, String category, String outcome, String targetType, String targetId) {}
