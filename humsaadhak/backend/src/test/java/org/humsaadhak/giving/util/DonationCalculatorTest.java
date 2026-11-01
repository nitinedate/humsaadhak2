package org.humsaadhak.giving.util;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.humsaadhak.shared.model.Periodicity;
import org.junit.jupiter.api.Test;

class DonationCalculatorTest {
    static BigDecimal r(long v) { return BigDecimal.valueOf(v); }

    @Test void paidAndPledgedStaySeparate() {
        var o = DonationCalculator.compute(r(450000), r(450000), r(500000), r(0), Periodicity.YEARLY, LocalDate.of(2026, 10, 24));
        assertThat(o.paid()).isEqualByComparingTo("450000");
        assertThat(o.variance()).isEqualByComparingTo("50000");
        assertThat(o.commitmentLeft()).isEqualByComparingTo("50000");
        assertThat(o.nextDue()).contains(LocalDate.of(2027, 10, 24));
    }

    @Test void cannotExceedWhatTheNeedStillNeeds() {
        assertThatThrownBy(() -> DonationCalculator.compute(r(460000), r(450000), null, null, Periodicity.ONE_TIME, LocalDate.now()))
                .hasMessage("This cause needs ₹4,50,000 at most.");
    }

    @Test void directGiftHasNoPledge() {
        var o = DonationCalculator.compute(r(25000), r(90000), null, null, Periodicity.ONE_TIME, LocalDate.now());
        assertThat(o.commitmentLeft()).isEqualByComparingTo("0");
        assertThat(o.nextDue()).isEmpty();
    }

    @Test void indianDigitGrouping() {
        assertThat(DonationCalculator.rupees(r(1240000))).isEqualTo("₹12,40,000");
        assertThat(DonationCalculator.rupees(r(999))).isEqualTo("₹999");
    }

    @Test void cycleStartFollowsFinancialYear() {
        assertThat(DonationCalculator.cycleStart(LocalDate.of(2026, 10, 24), Periodicity.QUARTERLY)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(DonationCalculator.cycleStart(LocalDate.of(2027, 2, 10), Periodicity.QUARTERLY)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(DonationCalculator.cycleStart(LocalDate.of(2027, 2, 10), Periodicity.YEARLY)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(DonationCalculator.cycleStart(LocalDate.of(2026, 10, 24), Periodicity.HALF_YEARLY)).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
