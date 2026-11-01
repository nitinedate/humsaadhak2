package org.humsaadhak.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import org.humsaadhak.TestPrincipal;
import org.humsaadhak.TestcontainersConfiguration;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Chart drill-downs, lookups and counters over HTTP against PostgreSQL (Docker), including who may see what. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DetailIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    long donor, otherDonor, ngo, project;

    long org(String type, String name) {
        return db.sql("insert into organisation(created_at, name, type, verified) values (now(), ?, ?, true) returning id").params(name, type).query(Long.class).single();
    }

    @BeforeEach
    void data() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        donor = org("DONOR_CORPORATE", "Drill Foundation " + System.nanoTime());
        otherDonor = org("DONOR_INDIVIDUAL", "Someone Else " + System.nanoTime());
        ngo = org("NGO", "Drill Trust " + System.nanoTime());
        project = db.sql("""
            insert into need(created_at, org_id, kind, name, description, sector, target_amount, raised_amount, periodicity, approved)
            values (now(), ?, 'PROJECT', 'Drill library', 'A description long enough for the validation rules to accept it.', 'Education', 500000, 300000, 'ONE_TIME', true)
            returning id""").param(ngo).query(Long.class).single();
        for (long[] gift : new long[][] { { donor, 200000 }, { otherDonor, 100000 } })
            db.sql("""
                insert into donation(created_at, donor_org_id, need_id, paid_amount, currency, payment_ref, paid_on, idempotency_key)
                values (now(), ?, ?, ?, 'INR', 'UTR', ?, gen_random_uuid()::text)""").params(gift[0], project, gift[1], LocalDate.now()).update();
    }

    @Test
    void aDonorSeesOnlyTheirOwnDonationsWhateverTheyAskFor() throws Exception {
        mvc.perform(get("/api/v2/dashboards/details").param("set", "DONATIONS").param("needKind", "PROJECT")
                        .with(user(TestPrincipal.of(1, Role.DONOR_CORPORATE, donor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].values.donorGroup").value("Corporate donor"))
                .andExpect(jsonPath("$.rows[0].values.beneficiaryGroup").value("Indirect beneficiary (NGO)"))
                .andExpect(jsonPath("$.columns[*].key", hasItems("ref", "paidOn", "donor", "amount")));
        mvc.perform(get("/api/v2/dashboards/details").param("set", "ORGANISATIONS").with(user(TestPrincipal.of(1, Role.DONOR_CORPORATE, donor))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aBeneficiarySeesItsProjectsAndEachOneOpensIntoItsDonations() throws Exception {
        mvc.perform(get("/api/v2/dashboards/details").param("set", "NEEDS").param("state", "RECEIVED").with(user(TestPrincipal.of(2, Role.NGO, ngo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].values.name").value("Drill library"))
                .andExpect(jsonPath("$.rows[0].values.remaining").value(200000))
                .andExpect(jsonPath("$.rows[0].children.title").value("Donations received"))
                .andExpect(jsonPath("$.rows[0].children.rows", hasSize(2)));
    }

    @Test
    void staffOpenOrganisationsIntoTheirCausesProjectsOrDonations() throws Exception {
        mvc.perform(get("/api/v2/dashboards/details").param("set", "ORGANISATIONS").param("type", "NGO").with(user(TestPrincipal.of(3, Role.MANAGER, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.values.id == " + ngo + ")].children.title").value(contains("Causes and projects")));
        mvc.perform(get("/api/v2/dashboards/details").param("set", "ORGANISATIONS").param("type", "SCHOOL' or '1'='1").with(user(TestPrincipal.of(3, Role.MANAGER, null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lookupsAndCountersServeTheForms() throws Exception {
        mvc.perform(get("/api/v2/lookups/open-needs").param("q", "drill library").with(user(TestPrincipal.of(1, Role.DONOR_CORPORATE, donor))))
                .andExpect(jsonPath("$[0].orgName").value(startsWith("Drill Trust")))
                .andExpect(jsonPath("$[0].remaining").value(200000))
                .andExpect(jsonPath("$[0].orgGroup").value("Indirect beneficiary (NGO)"));
        mvc.perform(get("/api/v2/lookups/organisations").param("group", "DONORS").param("q", "drill foundation").with(user(TestPrincipal.of(3, Role.ASSOCIATE, null))))
                .andExpect(jsonPath("$[0].group").value("Corporate donor"));
        mvc.perform(get("/api/v2/lookups/organisations").param("group", "DONORS").with(user(TestPrincipal.of(1, Role.DONOR_CORPORATE, donor))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/dashboards/me/counters").with(user(TestPrincipal.of(3, Role.MANAGER, null))))
                .andExpect(jsonPath("$.approvalsWaiting").isNumber()).andExpect(jsonPath("$.shareRequestsWaiting").value(0));
    }
}
