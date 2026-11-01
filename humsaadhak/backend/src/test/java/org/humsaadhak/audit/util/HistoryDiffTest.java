package org.humsaadhak.audit.util;

import org.humsaadhak.audit.dto.HistoryChange;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class HistoryDiffTest {

    private static Map<String, Object> row(int rev, int revType, Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("id", 7L, "rev", rev, "revtype", revType, "revtstmp", 1_700_000_000_000L + rev,
                "username", "priya@humsaadhak.org", "version", (long) rev, "updated_at", "t" + rev));
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void creationListsBusinessFieldsOnly() {
        var entries = HistoryDiff.entries(List.of(row(1, 0, "name", "Mid-day meals", "commitment_amount", new BigDecimal("200000.00"))));
        assertThat(entries.size()).isEqualTo(1);
        var e = entries.get(0);
        assertThat(e.kind()).isEqualTo("CREATED");
        assertThat(e.user()).isEqualTo("priya@humsaadhak.org");
        assertThat(e.changes().size()).isEqualTo(2);           // id, version, updated_at ... are hidden
        assertThat(e.changes().get(0)).isEqualTo(new HistoryChange("Commitment amount", null, "200000"));
    }

    @Test
    void updateShowsOnlyWhatChangedNewestFirst() {
        var entries = HistoryDiff.entries(List.of(
                row(1, 0, "name", "Mid-day meals", "commitment_amount", new BigDecimal("200000.00")),
                row(2, 1, "name", "Mid-day meals", "commitment_amount", new BigDecimal("300000.00")),
                row(3, 1, "name", "Mid-day meals for 400 children", "commitment_amount", new BigDecimal("300000"))));
        assertThat(entries.size()).isEqualTo(3);
        assertThat(entries.get(0).revision()).isEqualTo(3);
        assertThat(entries.get(0).changes()).isEqualTo(List.of(new HistoryChange("Name", "Mid-day meals", "Mid-day meals for 400 children")));
        assertThat(entries.get(1).changes()).isEqualTo(List.of(new HistoryChange("Commitment amount", "200000", "300000")));
    }

    @Test
    void revisionWithOnlyTechnicalChangesIsSkipped() {
        var entries = HistoryDiff.entries(List.of(row(1, 0, "name", "A"), row(2, 1, "name", "A")));
        assertThat(entries.size()).isEqualTo(1);
        assertThat(entries.get(0).kind()).isEqualTo("CREATED");
    }

    @Test
    void deletionIsAlwaysListed() {
        var entries = HistoryDiff.entries(List.of(row(1, 0, "name", "A"), row(2, 2, "name", "A")));
        assertThat(entries.get(0).kind()).isEqualTo("DELETED");
    }

    @Test
    void labelsAreReadable() {
        assertThat(HistoryDiff.label("target_amount")).isEqualTo("Target amount");
        assertThat(HistoryDiff.label("")).isEqualTo("");
    }
}
