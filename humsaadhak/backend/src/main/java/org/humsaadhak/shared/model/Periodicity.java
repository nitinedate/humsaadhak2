package org.humsaadhak.shared.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * How often a donor gives. Legacy ids 1–5 are kept for data migration.
 * nextDate() reproduces the legacy schedule: +1, +3, +6 or +12 months from the payment date.
 */
public enum Periodicity {
    ONE_TIME(1, 0, 0),
    MONTHLY(2, 1, 5),
    QUARTERLY(3, 3, 10),
    HALF_YEARLY(4, 6, 15),
    YEARLY(5, 12, 30);

    private final int legacyId;
    private final int months;
    private final int defaultReminderDays;

    Periodicity(int legacyId, int months, int defaultReminderDays) {
        this.legacyId = legacyId;
        this.months = months;
        this.defaultReminderDays = defaultReminderDays;
    }

    public int legacyId() { return legacyId; }
    public int defaultReminderDays() { return defaultReminderDays; }
    public boolean recurring() { return months > 0; }

    /** The next due date after a payment, or empty for one-time gifts. */
    public Optional<LocalDate> nextDate(LocalDate paidOn) {
        if (paidOn == null) throw new IllegalArgumentException("paidOn is required");
        return recurring() ? Optional.of(paidOn.plusMonths(months)) : Optional.empty();
    }

    /** True when today falls inside the reminder window [due − reminderDays, due]. */
    public static boolean inReminderWindow(LocalDate today, LocalDate due, int reminderDays) {
        LocalDate start = due.minusDays(reminderDays);
        return !today.isBefore(start) && !today.isAfter(due);
    }

    public static Periodicity fromLegacyId(int id) {
        for (Periodicity p : values()) if (p.legacyId == id) return p;
        throw new IllegalArgumentException("Unknown periodicity id " + id);
    }
}
