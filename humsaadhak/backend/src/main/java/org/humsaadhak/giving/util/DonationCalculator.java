package org.humsaadhak.giving.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.model.Periodicity;

/**
 * Donation arithmetic in one place (pure, unit-tested). Fixes D4: paid and pledged are separate numbers;
 * the variance is derived, never stored in a column called "amount".
 */
public final class DonationCalculator {
    private DonationCalculator() {}

    public record Outcome(BigDecimal paid, BigDecimal variance, BigDecimal commitmentLeft, Optional<LocalDate> nextDue) {}

    public static Outcome compute(BigDecimal paid, BigDecimal needRemaining, BigDecimal pledged,
                                  BigDecimal alreadyGivenThisCycle, Periodicity periodicity, LocalDate paidOn) {
        if (paid == null || paid.signum() <= 0)
            throw new DomainException("amount-required", "Enter an amount above zero.");
        if (needRemaining != null && paid.compareTo(needRemaining) > 0)
            throw new DomainException("over-target", "This cause needs " + rupees(needRemaining) + " at most.");
        BigDecimal pledge = pledged == null ? BigDecimal.ZERO : pledged;
        BigDecimal given = alreadyGivenThisCycle == null ? BigDecimal.ZERO : alreadyGivenThisCycle;
        BigDecimal variance = pledge.subtract(paid);
        BigDecimal left = pledge.subtract(given).subtract(paid).max(BigDecimal.ZERO);
        Optional<LocalDate> next = periodicity == null ? Optional.empty() : periodicity.nextDate(paidOn);
        return new Outcome(paid, variance, left, next);
    }

    /** Start of the current commitment cycle: calendar month, or quarter / half / year of the Indian financial year (from 1 April). */
    public static LocalDate cycleStart(LocalDate today, Periodicity p) {
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate fy = LocalDate.of(fyStartYear, 4, 1);
        int monthsIntoFy = (today.getMonthValue() + 8) % 12;
        return switch (p) {
            case MONTHLY -> today.withDayOfMonth(1);
            case QUARTERLY -> fy.plusMonths(monthsIntoFy / 3 * 3L);
            case HALF_YEARLY -> fy.plusMonths(monthsIntoFy / 6 * 6L);
            case YEARLY, ONE_TIME -> fy;
        };
    }

    /** ₹ with Indian digit grouping, e.g. ₹4,50,000. */
    public static String rupees(BigDecimal amount) { return org.humsaadhak.shared.util.IndianRupees.format(amount); }
}
