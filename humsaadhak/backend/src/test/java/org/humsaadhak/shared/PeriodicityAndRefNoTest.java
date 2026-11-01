package org.humsaadhak.shared;

import org.humsaadhak.shared.model.Periodicity;
import org.humsaadhak.shared.util.RefNo;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodicityAndRefNoTest {
    @Test void refNoKeepsLegacyRule() {
        assertThat(RefNo.of("DN", 45)).isEqualTo("DN100045");
        assertThat(RefNo.of(RefNo.MATCH_BATCH, 43)).isEqualTo("PMT100043");
        assertThatThrownBy(() -> RefNo.of("DN", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nextDateMatchesLegacySchedule() {
        LocalDate paid = LocalDate.of(2026, 10, 24);
        assertThat(Periodicity.ONE_TIME.nextDate(paid)).isEmpty();
        assertThat(Periodicity.MONTHLY.nextDate(paid)).contains(LocalDate.of(2026, 11, 24));
        assertThat(Periodicity.QUARTERLY.nextDate(paid)).contains(LocalDate.of(2027, 1, 24));
        assertThat(Periodicity.HALF_YEARLY.nextDate(paid)).contains(LocalDate.of(2027, 4, 24));
        assertThat(Periodicity.YEARLY.nextDate(paid)).contains(LocalDate.of(2027, 10, 24));
        assertThat(Periodicity.MONTHLY.nextDate(LocalDate.of(2027, 1, 31))).contains(LocalDate.of(2027, 2, 28));
    }

    @Test void reminderWindowIsInclusive() {
        LocalDate due = LocalDate.of(2026, 11, 15);
        assertThat(Periodicity.inReminderWindow(LocalDate.of(2026, 11, 5), due, 10)).isTrue();
        assertThat(Periodicity.inReminderWindow(LocalDate.of(2026, 11, 4), due, 10)).isFalse();
        assertThat(Periodicity.inReminderWindow(due, due, 10)).isTrue();
        assertThat(Periodicity.inReminderWindow(due.plusDays(1), due, 10)).isFalse();
    }

    @Test void legacyIdsMap() { assertThat(Periodicity.fromLegacyId(3)).isEqualTo(Periodicity.QUARTERLY); }
}
