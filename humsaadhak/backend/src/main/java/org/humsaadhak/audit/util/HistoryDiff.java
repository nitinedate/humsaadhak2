package org.humsaadhak.audit.util;

import org.humsaadhak.audit.dto.HistoryChange;
import org.humsaadhak.audit.dto.HistoryEntry;

import java.util.*;

/**
 * Turns consecutive audit rows into "what changed" entries (pure, unit-tested).
 * Technical columns are ignored so people only see business fields.
 */
public final class HistoryDiff {
    public static final Set<String> IGNORED = Set.of("id", "rev", "revtype", "version", "created_at", "created_by",
            "updated_at", "updated_by", "revtstmp", "username", "request_id", "storage_key", "legacy_id");

    private HistoryDiff() {}

    /** @param rows audit rows in revision order; each holds the column values plus rev, revtype, revtstmp and username */
    public static List<HistoryEntry> entries(List<Map<String, Object>> rows) {
        List<HistoryEntry> out = new ArrayList<>();
        Map<String, Object> previous = null;
        for (Map<String, Object> row : rows) {
            int revType = ((Number) row.getOrDefault("revtype", 1)).intValue();
            String kind = switch (revType) { case 0 -> "CREATED"; case 2 -> "DELETED"; default -> "UPDATED"; };
            List<HistoryChange> changes = new ArrayList<>();
            for (var e : new TreeMap<>(row).entrySet()) {
                String field = e.getKey();
                if (IGNORED.contains(field)) continue;
                Object before = previous == null ? null : previous.get(field);
                Object after = e.getValue();
                if (previous == null ? after != null : !Objects.equals(text(before), text(after)))
                    changes.add(new HistoryChange(label(field), text(before), text(after)));
            }
            if (!changes.isEmpty() || revType != 1)
                out.add(new HistoryEntry(((Number) row.get("rev")).intValue(), ((Number) row.getOrDefault("revtstmp", 0L)).longValue(),
                        String.valueOf(row.getOrDefault("username", "system")), kind, changes,
                        row.get("request_id") == null ? null : String.valueOf(row.get("request_id"))));
            previous = row;
        }
        Collections.reverse(out); // newest first
        return out;
    }

    /** commitment_amount -> Commitment amount */
    public static String label(String column) {
        String s = column.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String text(Object v) {
        if (v == null) return null;
        if (v instanceof java.math.BigDecimal b) return b.stripTrailingZeros().toPlainString();
        return String.valueOf(v);
    }
}
