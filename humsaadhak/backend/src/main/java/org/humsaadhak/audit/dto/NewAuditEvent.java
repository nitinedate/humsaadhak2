package org.humsaadhak.audit.dto;

import java.util.Set;

/** An audit record about to be written. */
public record NewAuditEvent(String requestId, Long actorId, String actorEmail, String actorName, String actorRole, Long actorOrgId,
                            String action, String category, String targetType, String targetId, Set<Long> orgIds, String outcome,
                            String errorCode, String detail, String ip, String userAgent) {}
