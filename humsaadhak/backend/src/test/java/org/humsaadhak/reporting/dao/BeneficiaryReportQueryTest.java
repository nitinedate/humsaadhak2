package org.humsaadhak.reporting.dao;

import org.humsaadhak.reporting.dto.ReportColumn;

import static org.assertj.core.api.Assertions.*;
import static org.humsaadhak.reporting.dao.BeneficiaryReportQuery.CustomColumn.*;

import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.reporting.dao.BeneficiaryReportQuery.Filter;
import org.humsaadhak.reporting.dao.BeneficiaryReportQuery.Type;
import org.humsaadhak.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class BeneficiaryReportQueryTest {
    private static Filter ngo(List<BeneficiaryReportQuery.CustomColumn> cols) { return new Filter(31, false, null, null, null, null, cols); }

    @Test
    void customColumnsAreTakenOnlyFromTheFixedListAndKeepTheOriginalOrder() {
        assertThat(BeneficiaryReportQuery.customColumns(List.of("DONOR_NAME", "NEED", "APPROVED_DATE", "NEED")))
                .isEqualTo(List.of(NEED, APPROVED_DATE, DONOR_NAME));
        assertThatThrownBy(() -> BeneficiaryReportQuery.customColumns(List.of("NEED", "benefactor_name; drop table donation")))
                .isInstanceOf(DomainException.class).hasMessageContaining("Unknown column");
        assertThatThrownBy(() -> BeneficiaryReportQuery.customColumns(List.of()))
                .hasMessageContaining("at least one column");
    }

    @Test
    void customReportJoinsOnlyWhatTheChosenColumnsNeed() {
        var q = BeneficiaryReportQuery.build(Type.CUSTOM, ngo(List.of(NEED, APPROVED_DATE)));
        assertThat(q.select().contains("join match")).isFalse();
        assertThat(q.columns().stream().map(ReportColumn::label).toList())
                .isEqualTo(List.of("Project", "Project Id", "Approved Date"));

        var full = BeneficiaryReportQuery.build(Type.CUSTOM, ngo(List.of(NEED, DONOR_NAME, UPCOMING_DATE, DOC_STATUS)));
        assertThat(full.select().contains("left join match m")).isTrue();
        assertThat(full.select().contains("left join donation d")).isTrue();
        assertThat(full.select().contains("left join organisation donor")).isTrue();
        assertThat(full.select().contains("left join lateral")).isTrue();
        assertThat(full.columns().get(full.columns().size() - 1).label()).isEqualTo("Project Doc status");
        assertThat(full.params()).isEqualTo(List.of(31L));
    }

    @Test
    void schoolsSeeCauseLabels() {
        var q = BeneficiaryReportQuery.build(Type.CUSTOM, new Filter(21, true, null, null, null, null, List.of(PENDING_NEED, DOC_STATUS)));
        assertThat(q.columns().stream().map(ReportColumn::label).toList()).isEqualTo(List.of("Cause", "Cause Id", "Cause Doc status"));
        assertThat(q.countSql().contains("not n.approved")).isTrue();
        assertThat(BeneficiaryReportQuery.build(Type.NEEDS, new Filter(21, true, null, null, null, null, null)).columns().get(0).label()).isEqualTo("Cause Name");
    }

    @Test
    void exactlyOneOfApprovedOrNotYetApprovedIsRequired() {
        assertThatThrownBy(() -> BeneficiaryReportQuery.build(Type.CUSTOM, ngo(List.of(APPROVED_DATE))))
                .hasMessageContaining("Choose either “Project” or “Project (yet to be approved)”");
        assertThatThrownBy(() -> BeneficiaryReportQuery.build(Type.CUSTOM, ngo(List.of(NEED, PENDING_NEED)))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> BeneficiaryReportQuery.build(Type.CUSTOM, new Filter(31, false, null, "2025-2026", null, null, List.of(PENDING_NEED))))
                .hasMessageContaining("do not apply");
    }

    @Test
    void financialYearsRunFromAprilToMarch() {
        assertThat(BeneficiaryReportQuery.financialYear("2025-2026")).isEqualTo(new LocalDate[] { LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31) });
        assertThatThrownBy(() -> BeneficiaryReportQuery.financialYear("2025-2027")).hasMessageContaining("2025-2026");
        assertThatThrownBy(() -> BeneficiaryReportQuery.financialYear("2025")).hasMessageContaining("2025-2026");
        assertThat(BeneficiaryReportQuery.years(LocalDate.of(2024, 2, 1), LocalDate.of(2026, 10, 24)))
                .isEqualTo(List.of("2026-2027", "2025-2026", "2024-2025", "2023-2024"));
    }

    @Test
    void filtersAreScopedToTheOrganisationAndBoundAsParameters() {
        var q = BeneficiaryReportQuery.build(Type.DONATIONS, new Filter(31, false, 219L, "2025-2026", null, null, null));
        assertThat(q.countSql()).isEqualTo("select count(*) from donation d join need n on n.id = d.need_id join organisation donor on donor.id = d.donor_org_id"
                + " left join purpose p on p.id = d.purpose_id where n.org_id = ? and n.id = ? and n.approved and n.approved_at >= ? and n.approved_at < ?");
        assertThat(q.params().size()).isEqualTo(4);
        assertThat(q.params().get(0)).isEqualTo(31L);
        assertThat(q.params().get(1)).isEqualTo(219L);
        assertThat(q.select().endsWith("order by d.paid_on desc, d.id desc limit ? offset ?")).isTrue();
        assertThatThrownBy(() -> BeneficiaryReportQuery.build(Type.MATCHES, new Filter(31, false, null, "2025-2026", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 6, 1), null)))
                .hasMessageContaining("not both");
        assertThatThrownBy(() -> BeneficiaryReportQuery.build(Type.MATCHES, new Filter(31, false, null, null, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 1), null)))
                .hasMessageContaining("before the start");
    }

    @Test
    void projectTotalsUseSeparateSubqueriesSoTheyNeverDoubleCount() {
        var q = BeneficiaryReportQuery.build(Type.NEEDS, ngo(null));
        assertThat(q.select().contains("(select coalesce(sum(p.commitment_amount), 0) from match m join purpose p on p.id = m.purpose_id where m.need_id = n.id)")).isTrue();
        assertThat(q.select().contains("(select coalesce(sum(d.paid_amount), 0) from donation d where d.need_id = n.id)")).isTrue();
        assertThat(q.countSql()).isEqualTo("select count(*) from need n where n.org_id = ? and n.approved");
    }

    @Test
    void exportHeaderLinesMatchTheOriginal() {
        assertThat(BeneficiaryReportQuery.entityLine("Pragati Trust", "IB100031")).isEqualTo("Entity Name - Pragati Trust [ID: IB100031]");
        assertThat(BeneficiaryReportQuery.filterLine(new Filter(31, false, 219L, "2025-2026", null, null, null), "Digital literacy lab"))
                .isEqualTo("Financial year - 2025-2026; Project - Digital literacy lab");
        assertThat(BeneficiaryReportQuery.filterLine(new Filter(21, true, null, null, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 9, 30), null), null))
                .isEqualTo("Approved between - 01-Apr-2025 to 30-Sep-2025");
        assertThat(BeneficiaryReportQuery.filterLine(ngo(List.of(PENDING_NEED)), null)).isEqualTo("Not yet approved");
    }
}
