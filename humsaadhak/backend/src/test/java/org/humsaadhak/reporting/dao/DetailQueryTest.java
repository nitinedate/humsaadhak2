package org.humsaadhak.reporting.dao;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.humsaadhak.reporting.dao.DetailQuery.Scope;
import org.humsaadhak.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class DetailQueryTest {

    @Test
    void staffSeeOrganisationsFilteredByTypeAndVerification() {
        var q = DetailQuery.build(DetailQuery.Set.ORGANISATIONS, Map.of("type", "SCHOOL", "verified", "true"), Scope.staffScope());
        assertThat(q.select().contains("where o.type = ? and o.verified = ?")).isTrue();
        assertThat(q.params()).isEqualTo(List.of("SCHOOL", true));
        assertThat(q.countSql()).isEqualTo("select count(*) from organisation o where o.type = ? and o.verified = ?");
        assertThat(q.select().endsWith("order by o.name limit 500")).isTrue();
    }

    @Test
    void donorsAreAlwaysLimitedToTheirOwnDonations() {
        var q = DetailQuery.build(DetailQuery.Set.DONATIONS, Map.of("needKind", "CAUSE", "from", "2026-04-01"), Scope.donor(11));
        assertThat(q.countSql().contains("where d.donor_org_id = ? and n.kind = ? and d.paid_on >= ?")).isTrue();
        assertThat(q.params()).isEqualTo(List.of(11L, "CAUSE", LocalDate.of(2026, 4, 1)));
    }

    @Test
    void beneficiariesSeeOnlyTheirOwnNeedsAndDonations() {
        assertThat(DetailQuery.build(DetailQuery.Set.NEEDS, Map.of("state", "RECEIVED"), Scope.beneficiary(31)).params()).isEqualTo(List.of(31L));
        var d = DetailQuery.build(DetailQuery.Set.DONATIONS, Map.of("donorType", "DONOR_CORPORATE"), Scope.beneficiary(31));
        assertThat(d.countSql().contains("where n.org_id = ? and donor.type = ?")).isTrue();
    }

    @Test
    void listsOutsideAPersonsRoleAreRefused() {
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.ORGANISATIONS, Map.of(), Scope.donor(11))).isInstanceOf(DetailQuery.NotAllowed.class);
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.NEEDS, Map.of(), Scope.donor(11))).isInstanceOf(DetailQuery.NotAllowed.class);
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.PURPOSES, Map.of(), Scope.beneficiary(31))).isInstanceOf(DetailQuery.NotAllowed.class);
    }

    @Test
    void onlyAllowListedFiltersAndValuesAreAccepted() {
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.ORGANISATIONS, Map.of("type", "SCHOOL' or 1=1 --"), Scope.staffScope()))
                .isInstanceOf(DomainException.class).hasMessageContaining("Unknown type");
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.NEEDS, Map.of("orderBy", "name"), Scope.staffScope()))
                .hasMessageContaining("Unknown filter orderBy");
        assertThatThrownBy(() -> DetailQuery.build(DetailQuery.Set.NEEDS, Map.of("id", "1;drop"), Scope.staffScope())).hasMessageContaining("Not a number");
        assertThatThrownBy(() -> DetailQuery.set("users")).hasMessageContaining("Unknown detail set");
        // free text (sector) is never put into the SQL, only bound as a parameter
        var q = DetailQuery.build(DetailQuery.Set.DONATIONS, Map.of("sector", "Education'; drop table donation; --"), Scope.staffScope());
        assertThat(q.select().contains("drop table")).isFalse();
        assertThat(q.params()).isEqualTo(List.of("Education'; drop table donation; --"));
    }

    @Test
    void periodsCoverMonthsAndFinancialYearQuarters() {
        assertThat(DetailQuery.period(new HashMap<>(Map.of("month", "2026-02")))).isEqualTo(new LocalDate[] { LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28) });
        assertThat(DetailQuery.period(new HashMap<>(Map.of("quarter", "Q1", "fy", "2026")))).isEqualTo(new LocalDate[] { LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30) });
        assertThat(DetailQuery.period(new HashMap<>(Map.of("quarter", "Q4", "fy", "2026")))).isEqualTo(new LocalDate[] { LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31) });
        assertThatThrownBy(() -> DetailQuery.period(new HashMap<>(Map.of("quarter", "Q2")))).hasMessageContaining("needs fy");
        assertThatThrownBy(() -> DetailQuery.period(new HashMap<>(Map.of("month", "October")))).hasMessageContaining("2026-10");
    }

    @Test
    void needStatesMatchTheDashboardBuckets() {
        assertThat(DetailQuery.needState("MATCHED_DONATED")).isEqualTo("n.approved and exists (select 1 from match m where m.need_id = n.id) and n.raised_amount > 0");
        assertThat(DetailQuery.needState("NEITHER")).isEqualTo("n.approved and not exists (select 1 from match m where m.need_id = n.id) and n.raised_amount = 0");
        assertThatThrownBy(() -> DetailQuery.needState("ALL")).isInstanceOf(DomainException.class);
    }
}
