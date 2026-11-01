package org.humsaadhak.audit.dto;

import java.time.Instant;
import java.util.List;

/**
 * One line of an audit trail: who did what to which record, when, from where, how it ended and which fields changed.
 * ip and userAgent are filled only for people with audit.view.
 */
public record AuditEntry(long id, Instant at, String actorName, String actorEmail, String actorRole, String action, String label,
                         String category, String targetType, String targetId, String outcome, String errorCode, String detail,
                         String ip, String userAgent, String requestId, List<HistoryChange> changes) {}
