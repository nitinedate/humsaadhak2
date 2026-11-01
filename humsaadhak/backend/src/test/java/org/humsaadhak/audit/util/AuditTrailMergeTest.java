package org.humsaadhak.audit.util;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.humsaadhak.audit.dto.AuditEntry;
import org.humsaadhak.audit.dto.HistoryChange;
import org.humsaadhak.audit.dto.HistoryEntry;
import org.junit.jupiter.api.Test;

class AuditTrailMergeTest {
    static AuditEntry event(long id, String at, String action, String requestId) {
        return new AuditEntry(id, Instant.parse(at), "Priya Mehta", "priya@x.org", "Manager", action, AuditActions.label(action),
                AuditActions.category(action), "PURPOSE", "7", "SUCCESS", null, null, "10.0.0.5", "Firefox", requestId, List.of());
    }

    static HistoryEntry change(int rev, String at, String requestId, String user, String kind, HistoryChange... changes) {
        return new HistoryEntry(rev, Instant.parse(at).toEpochMilli(), user, kind, List.of(changes), requestId);
    }

    @Test
    void eachActionShowsTheFieldsItChanged() {
        List<AuditEntry> trail = AuditTrailMerge.merge(
                List.of(event(2, "2026-10-02T10:00:00Z", "purpose.update", "req-2"), event(1, "2026-10-01T09:00:00Z", "purpose.create", "req-1")),
                List.of(change(11, "2026-10-01T09:00:00Z", "req-1", "priya@x.org", "CREATED", new HistoryChange("Name", null, "Library books")),
                        change(12, "2026-10-02T10:00:00Z", "req-2", "priya@x.org", "UPDATED", new HistoryChange("Commitment amount", "500000", "600000"))),
                "PURPOSE", "7");
        assertThat(trail.size()).isEqualTo(2);
        assertThat(trail.get(0).action()).isEqualTo("purpose.update");
        assertThat(trail.get(0).changes().get(0).after()).isEqualTo("600000");
        assertThat(trail.get(1).changes().get(0).field()).isEqualTo("Name");
    }

    @Test
    void changesNoActionExplainsGetTheirOwnLine() {
        List<AuditEntry> trail = AuditTrailMerge.merge(List.of(event(5, "2026-10-03T08:00:00Z", "approval.approve", "req-5")),
                List.of(change(20, "2026-10-04T01:30:00Z", null, "system", "UPDATED", new HistoryChange("Completed", "false", "true")),
                        change(3, "2026-09-01T12:00:00Z", null, "old.user@x.org", "CREATED", new HistoryChange("Name", null, "Old purpose"))),
                "PURPOSE", "7");
        assertThat(trail.size()).isEqualTo(3);
        assertThat(trail.get(0).action()).isEqualTo("record.change");          // newest first
        assertThat(trail.get(0).detail()).isEqualTo("Made by the system");
        assertThat(trail.get(1).action()).isEqualTo("approval.approve");
        assertThat(trail.get(2).action()).isEqualTo("record.create");
        assertThat(trail.get(2).actorEmail()).isEqualTo("old.user@x.org");
    }

    @Test
    void redactionRemovesAddressAndBrowserOnly() {
        AuditEntry r = AuditTrailMerge.redact(event(1, "2026-10-01T09:00:00Z", "purpose.create", "req-1"));
        assertThat(r.ip()).isEqualTo(null);
        assertThat(r.userAgent()).isEqualTo(null);
        assertThat(r.actorName()).isEqualTo("Priya Mehta");
        assertThat(AuditTrailMerge.page(List.of(1, 2, 3, 4, 5), 1, 2)).isEqualTo(List.of(3, 4));
        assertThat(AuditTrailMerge.page(List.of(1, 2), 3, 2)).isEmpty();
    }
}
