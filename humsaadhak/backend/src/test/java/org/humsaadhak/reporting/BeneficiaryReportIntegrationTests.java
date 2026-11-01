package org.humsaadhak.reporting;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.humsaadhak.TestPrincipal;
import org.humsaadhak.TestcontainersConfiguration;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The original project's reports over HTTP against PostgreSQL (Docker): totals, scope and the custom report. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BeneficiaryReportIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    long ngo, donorA, donorB, project;

    private long insert(String sql, Object... params) { return db.sql(sql + " returning id").params(params).query(Long.class).single(); }

    @BeforeEach
    void data() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        long n = System.nanoTime();
        ngo = insert("insert into organisation(created_at, name, type, ref_no, verified) values (now(), ?, 'NGO', ?, true)", "Report Trust " + n, "IB" + n % 100000000);
        donorA = insert("insert into organisation(created_at, name, type, ref_no, verified) values (now(), 'Donor A', 'DONOR_CORPORATE', ?, true)", "BNA" + n % 10000000);
        donorB = insert("insert into organisation(created_at, name, type, ref_no, verified) values (now(), 'Donor B', 'DONOR_INDIVIDUAL', ?, true)", "BNB" + n % 10000000);
        project = insert("""
            insert into need(created_at, org_id, kind, name, description, sector, target_amount, raised_amount, periodicity, approved, approved_at)
            values (now(), ?, 'PROJECT', 'Library on wheels', 'A description long enough for the validation rules to accept it.', 'Education', 900000, 300000, 'ONE_TIME', true, '2025-06-10T10:00:00Z')""", ngo);
        long run = insert("insert into match_run(created_at, started_by, model_version, purpose_count, candidate_count) values (now(), 0, 't', 2, 2)");
        for (long[] d : new long[][] { { donorA, 500000, 200000 }, { donorB, 100000, 100000 } }) {
            long purpose = insert("""
                insert into purpose(created_at, donor_org_id, name, description, sector, commitment_amount, periodicity, approved, commitment_date)
                values (now(), ?, 'Reading for all', 'A description long enough for the validation rules to accept it.', 'Education', ?, 'YEARLY', true, '2025-05-01')""", d[0], d[1]);
            long cand = insert("insert into match_candidate(created_at, run_id, purpose_id, need_id, final_score, engine_rank, model_version, decision) values (now(), ?, ?, ?, 90, 1, 't', 'APPROVED')", run, purpose, project);
            long batch = insert("insert into match_batch(created_at, purpose_id, confirmed_by) values (now(), ?, 0)", purpose);
            insert("insert into match(created_at, batch_id, candidate_id, purpose_id, need_id, donor_org_id, priority) values (now(), ?, ?, ?, ?, ?, 1)", batch, cand, purpose, project, d[0]);
            insert("insert into donation(created_at, donor_org_id, need_id, purpose_id, paid_amount, currency, payment_ref, paid_on, idempotency_key) values (now(), ?, ?, ?, ?, 'INR', 'UTR', ?, gen_random_uuid()::text)",
                    d[0], project, purpose, d[2], LocalDate.of(2025, 7, 1));
        }
    }

    @Test
    void projectReportTotalsAreNotDoubleCounted() throws Exception {
        // the original joined matches and donations together: 2 x 2 rows would have shown 1,200,000 committed and 600,000 donated
        mvc.perform(post("/api/v2/beneficiary-reports/needs").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"year\":\"2025-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Project Report"))
                .andExpect(jsonPath("$.rows[0].commitmentTotal").value(600000))
                .andExpect(jsonPath("$.rows[0].donationTotal").value(300000))
                .andExpect(jsonPath("$.rows[0].matched").value("Y"))
                .andExpect(jsonPath("$.entityLine", startsWith("Entity Name - Report Trust")));
    }

    @Test
    void donationAndMatchReportsListEachRow() throws Exception {
        mvc.perform(post("/api/v2/beneficiary-reports/donations").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows[*].donorType", containsInAnyOrder("Corporate donor", "Individual donor")))
                .andExpect(jsonPath("$.rows[0].periodicity").value("Yearly"));
        mvc.perform(post("/api/v2/beneficiary-reports/matches").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approvedFrom\":\"2026-01-01\",\"approvedTo\":\"2026-03-31\"}"))
                .andExpect(jsonPath("$.total").value(0));                     // approved in June 2025, outside the range
    }

    @Test
    void customReportShowsOnlyTheChosenColumns() throws Exception {
        mvc.perform(post("/api/v2/beneficiary-reports/custom").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"columns\":[\"DONOR_NAME\",\"NEED\",\"DONATION_AMOUNT\"],\"header\":\"Library donors\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Library donors"))
                .andExpect(jsonPath("$.columns[*].label", contains("Project", "Project Id", "Donation Amount", "Donor Name")))
                .andExpect(jsonPath("$.rows[*].donorName", hasItem(startsWith("Donor A [ID: BNA"))));
        mvc.perform(post("/api/v2/beneficiary-reports/custom").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"columns\":[\"NEED\",\"(select password_hash from app_user)\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("bad-report"));
    }

    @Test
    void scope() throws Exception {
        mvc.perform(get("/api/v2/beneficiary-reports/options").with(user(TestPrincipal.of(2, Role.DONOR_CORPORATE, donorA)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/beneficiary-reports/options").with(user(TestPrincipal.of(3, Role.MANAGER, null))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("org-required"));
        mvc.perform(get("/api/v2/beneficiary-reports/options").param("orgId", String.valueOf(ngo)).with(user(TestPrincipal.of(3, Role.MANAGER, null))))
                .andExpect(jsonPath("$.organisation.group").value("Indirect beneficiary (NGO)"))
                .andExpect(jsonPath("$.years", hasItem("2025-2026")))
                .andExpect(jsonPath("$.customColumns[0].label").value("Project"));
        // an NGO cannot point the report at someone else's project
        long other = insert("insert into organisation(created_at, name, type) values (now(), 'Other NGO', 'NGO')");
        long theirs = insert("""
            insert into need(created_at, org_id, kind, name, description, sector, target_amount, periodicity, approved)
            values (now(), ?, 'PROJECT', 'Theirs', 'A description long enough for the validation rules to accept it.', 'Education', 1000, 'ONE_TIME', true)""", other);
        mvc.perform(post("/api/v2/beneficiary-reports/donations").with(user(TestPrincipal.of(1, Role.NGO, ngo))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"needId\":" + theirs + "}"))
                .andExpect(status().isNotFound());
    }
}
