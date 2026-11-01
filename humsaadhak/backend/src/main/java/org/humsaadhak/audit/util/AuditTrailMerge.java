package org.humsaadhak.audit.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.humsaadhak.audit.dto.AuditEntry;
import org.humsaadhak.audit.dto.HistoryChange;
import org.humsaadhak.audit.dto.HistoryEntry;

/**
 * Joins a record's recorded actions with the field changes Envers kept for it (pure, unit-tested). Both carry the id of
 * the request that made them, so each action shows what it changed; changes no action explains (scheduled jobs, the
 * legacy migration, edits from before the audit trail existed) appear on their own lines.
 */
public final class AuditTrailMerge {
    private AuditTrailMerge() {}

    public static List<AuditEntry> merge(List<AuditEntry> events, List<HistoryEntry> history, String targetType, String targetId) {
        List<AuditEntry> out = new ArrayList<>();
        Set<HistoryEntry> used = new HashSet<>();
        for (AuditEntry e : events) {
            List<HistoryChange> changes = new ArrayList<>(e.changes());
            if (e.requestId() != null)
                for (HistoryEntry h : history)
                    if (e.requestId().equals(h.requestId())) { changes.addAll(h.changes()); used.add(h); }
            out.add(withChanges(e, changes));
        }
        for (HistoryEntry h : history) {
            if (used.contains(h)) continue;
            String action = "CREATED".equals(h.kind()) ? "record.create" : "record.change";
            out.add(new AuditEntry(-h.revision(), Instant.ofEpochMilli(h.timestamp()), null, h.user(), null, action, AuditActions.label(action),
                    "record", targetType, targetId, "SUCCESS", null, "system".equals(h.user()) ? "Made by the system" : null, null, null,
                    h.requestId(), h.changes()));
        }
        out.sort(Comparator.comparing(AuditEntry::at).reversed().thenComparing(AuditEntry::id, Comparator.reverseOrder()));
        return out;
    }

    public static AuditEntry withChanges(AuditEntry e, List<HistoryChange> changes) {
        return new AuditEntry(e.id(), e.at(), e.actorName(), e.actorEmail(), e.actorRole(), e.action(), e.label(), e.category(), e.targetType(),
                e.targetId(), e.outcome(), e.errorCode(), e.detail(), e.ip(), e.userAgent(), e.requestId(), List.copyOf(changes));
    }

    /** Without the client address and browser, for people without audit.view. */
    public static AuditEntry redact(AuditEntry e) {
        return new AuditEntry(e.id(), e.at(), e.actorName(), e.actorEmail(), e.actorRole(), e.action(), e.label(), e.category(), e.targetType(),
                e.targetId(), e.outcome(), e.errorCode(), e.detail(), null, null, e.requestId(), e.changes());
    }

    public static <T> List<T> page(List<T> all, int page, int size) {
        int from = Math.min(all.size(), page * size);
        return all.subList(from, Math.min(all.size(), from + size));
    }
}
