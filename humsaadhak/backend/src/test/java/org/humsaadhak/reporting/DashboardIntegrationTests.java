package org.humsaadhak.reporting;

import org.humsaadhak.reporting.dto.Drill;
import org.humsaadhak.reporting.dto.Dashboard;
import org.humsaadhak.reporting.dto.BarChart;
import org.humsaadhak.reporting.dto.Bar;
import org.humsaadhak.reporting.dto.Donut;
import org.humsaadhak.reporting.dto.Slice;
import org.humsaadhak.reporting.service.DashboardService;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Runs the real dashboard SQL against PostgreSQL (Docker) with a small known data set. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class DashboardIntegrationTests {
    @Autowired DashboardService dashboards;
    @Autowired JdbcClient db;
    static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    long individual, corporate, school, ngo;

    long org(String type, String name, boolean verified) {
        return db.sql("insert into organisation(created_at, name, type, verified) values (now(), ?, ?, ?) returning id")
                .params(name, type, verified).query(Long.class).single();
    }

    long need(long orgId, String kind, double target) {
        return db.sql("""
            insert into need(created_at, org_id, kind, name, description, sector, target_amount, periodicity, approved)
            values (now(), ?, ?, ?, 'A description long enough for the validation rules to accept it.', 'Education', ?, 'ONE_TIME', true)
            returning id""").params(orgId, kind, kind + " of " + orgId, target).query(Long.class).single();
    }

    void donate(long donorOrgId, long needId, double amount, LocalDate on) {
        db.sql("""
            insert into donation(created_at, donor_org_id, need_id, paid_amount, currency, payment_ref, paid_on, idempotency_key)
            values (now(), ?, ?, ?, 'INR', 'UTR', ?, gen_random_uuid()::text)""").params(donorOrgId, needId, amount, on).update();
        db.sql("update need set raised_amount = raised_amount + ? where id = ?").params(amount, needId).update();
    }

    @BeforeEach
    void data() {
        individual = org("DONOR_INDIVIDUAL", "Vikram Rao", true);
        corporate = org("DONOR_CORPORATE", "Asha Foundation CSR", true);
        school = org("SCHOOL", "ZP School Khed", true);
        ngo = org("NGO", "Pragati Trust", false);
        long cause = need(school, "CAUSE", 450000);
        long project = need(ngo, "PROJECT", 520000);
        donate(individual, cause, 110000, LocalDate.of(2026, 5, 10));      // Q1
        donate(corporate, cause, 200000, LocalDate.of(2026, 8, 2));        // Q2
        donate(corporate, project, 120000, LocalDate.of(2026, 10, 1));     // Q3
        donate(corporate, project, 99000, LocalDate.of(2026, 2, 1));       // previous financial year: excluded
    }

    @Test
    void managerSeesAllFourGroupsAndWhoGaveToWhom() {
        Dashboard d = dashboards.manager(TODAY);
        Donut byType = d.donuts().get(0);
        assertThat(byType.slices().stream().map(Slice::label).toList()).isEqualTo(List.of(
                "Individual donors", "Corporate donors", "Direct beneficiaries (schools)", "Indirect beneficiaries (NGOs)"));
        BarChart flows = d.bars().stream().filter(b -> b.title().equals("Who gave to whom")).findFirst().orElseThrow();
        assertThat(flows.bars().stream().map(Bar::values).toList()).isEqualTo(List.of(List.of(110000.0, 0.0), List.of(200000.0, 120000.0)));
        assertThat(byType.slices().get(2).drill()).isEqualTo(Drill.of("ORGANISATIONS", "type", "SCHOOL"));
    }

    @Test
    void donorSeesDirectAndIndirectBeneficiaries() {
        Dashboard d = dashboards.donor(corporate, TODAY);
        assertThat(d.kpis().get(1).note()).isEqualTo("1 direct and 1 indirect beneficiaries");
        Donut where = d.donuts().get(0);
        assertThat(where.slices().get(0).value()).isEqualTo(200000.0);
        assertThat(where.slices().get(1).value()).isEqualTo(120000.0);
        assertThat(where.slices().get(0).drill()).isEqualTo(Drill.of("DONATIONS", "needKind", "CAUSE", "from", "2026-04-01"));
        assertThat(d.bars().get(0).bars().stream().map(Bar::label).toList()).isEqualTo(List.of("Q1", "Q2", "Q3", "Q4"));
        assertThat(d.bars().get(0).bars().get(1).values()).isEqualTo(List.of(200000.0, 0.0));
    }

    @Test
    void beneficiarySeesIndividualAndCorporateDonors() {
        Dashboard d = dashboards.beneficiary(school, "SCHOOL", TODAY);
        assertThat(d.title()).isEqualTo("Direct beneficiary dashboard");
        assertThat(d.kpis().get(1).note()).isEqualTo("1 corporate, 1 individual");
        assertThat(d.donuts().get(1).slices().stream().map(Slice::value).toList()).isEqualTo(List.of(110000.0, 200000.0));
        assertThat(dashboards.beneficiary(ngo, "NGO", TODAY).title()).isEqualTo("Indirect beneficiary dashboard");
    }
}
