package org.humsaadhak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import org.awaitility.Awaitility;
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

/** Reference-data admin, mandatory-rule re-verification and CSV reports, end to end against PostgreSQL (Docker). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReferenceAndReportsIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    final TestPrincipal admin = TestPrincipal.of(900_001, Role.SUPER_ADMIN, null);
    final TestPrincipal manager = TestPrincipal.of(900_002, Role.MANAGER, null);

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build(); }

    @Test
    void documentTypesForFormsSayWhichOrganisationTypesMustUploadThem() throws Exception {
        // From document_type_rule (V2): the upload form highlights these as compulsory for the user's organisation type
        mvc.perform(get("/api/v2/reference/document-types")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SCHOOL_UDISE')].mandatoryFor[*]", contains("SCHOOL")))
                .andExpect(jsonPath("$[?(@.code == 'PAN')].mandatoryFor[*]", hasItems("DONOR_CORPORATE", "DONOR_INDIVIDUAL", "NGO", "SCHOOL")));
    }

    @Test
    void sectorsCanBeAddedAndSwitchedOffButUsedOnesAreNeverRenamed() throws Exception {
        mvc.perform(post("/api/v2/admin/sectors").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sports and play\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("SPORTS_AND_PLAY"));
        mvc.perform(get("/api/v2/reference/sectors")).andExpect(jsonPath("$[*].name", hasItem("Sports and play")));

        mvc.perform(put("/api/v2/admin/sectors/SPORTS_AND_PLAY").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sports and play\",\"active\":false}")).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v2/reference/sectors")).andExpect(jsonPath("$[*].name", not(hasItem("Sports and play"))));

        long org = db.sql("insert into organisation(created_at, name, type) values (now(), 'Sector test NGO', 'NGO') returning id").query(Long.class).single();
        db.sql("insert into need(created_at, org_id, kind, name, description, sector, target_amount, periodicity) values (now(), ?, 'PROJECT', 'X', 'A description that is long enough for the rules.', 'Nutrition', 100, 'ONE_TIME')")
                .param(org).update();
        mvc.perform(put("/api/v2/admin/sectors/NUTRITION").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Food\",\"active\":true}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("in-use"));

        mvc.perform(get("/api/v2/admin/sectors").with(user(manager))).andExpect(status().isForbidden());
    }

    @Test
    void changingMandatoryDocumentsRechecksVerification() throws Exception {
        long school = db.sql("insert into organisation(created_at, name, type, verified) values (now(), 'Rule test school', 'SCHOOL', true) returning id")
                .query(Long.class).single();
        long newType = db.sql("select id from document_type where code = 'NEED_PROPOSAL'").query(Long.class).single();
        mvc.perform(put("/api/v2/admin/document-types/{id}", newType).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Proposal\",\"shareable\":true,\"active\":true,\"mandatoryFor\":[\"SCHOOL\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mandatoryFor", contains("SCHOOL")));
        // The school has not uploaded a proposal, so it is no longer verified once the listener has run.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(db.sql("select verified from organisation where id = ?").param(school).query(Boolean.class).single()).isFalse());
    }

    @Test
    void reportsAreCsvForStaffOnly() throws Exception {
        mvc.perform(get("/api/v2/reports/organisations.csv").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("organisations-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("\uFEFFReference,Name,Type,City,State,Verified,Registered on")));
        mvc.perform(get("/api/v2/reports/donations.csv").param("from", "2026-10-01").param("to", "2026-04-01").with(user(manager)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v2/reports/needs.csv").with(user(TestPrincipal.of(900_003, Role.NGO, 1L)))).andExpect(status().isForbidden());
    }
}
