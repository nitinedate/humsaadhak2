package org.humsaadhak.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** The audit trail end to end against PostgreSQL (Docker): actions, their field changes, visibility, refusals, immutability. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditTrailIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    final TestPrincipal manager = TestPrincipal.of(900_202, Role.MANAGER, null);

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build(); }

    long organisation(String name, String type) {
        return db.sql("insert into organisation(created_at, name, type) values (now(), ?, ?) returning id").params(name, type).query(Long.class).single();
    }

    static String purpose(long amount) {
        return """
                {"name":"Library books for Class 5","description":"Story books and a reading corner for forty children in Class 5.",
                 "sector":"Education","commitmentAmount":%d,"periodicity":"YEARLY"}""".formatted(amount);
    }

    @Test
    void eachActionIsRecordedWithTheFieldsItChangedAndShownOnlyToItsOwners() throws Exception {
        long donorOrg = organisation("Audit test donor", "DONOR_CORPORATE");
        TestPrincipal donor = TestPrincipal.of(900_201, Role.DONOR_CORPORATE, donorOrg);
        String created = mvc.perform(post("/api/v2/purposes").with(user(donor)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(purpose(500000)))
                .andExpect(status().isCreated()).andExpect(header().exists("X-Request-Id")).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        mvc.perform(put("/api/v2/purposes/" + id).with(user(donor)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(purpose(600000)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v2/audit/trail").param("type", "PURPOSE").param("id", String.valueOf(id)).with(user(donor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("purpose.update"))
                .andExpect(jsonPath("$.items[0].label").value("Edited the purpose"))
                .andExpect(jsonPath("$.items[0].changes[?(@.field == 'Commitment amount')].after").value(org.hamcrest.Matchers.hasItem("600000")))
                .andExpect(jsonPath("$.items[0].ip").doesNotExist())          // only audit.view sees addresses
                .andExpect(jsonPath("$.items[1].action").value("purpose.create"));

        TestPrincipal stranger = TestPrincipal.of(900_203, Role.DONOR_INDIVIDUAL, organisation("Someone else", "DONOR_INDIVIDUAL"));
        mvc.perform(get("/api/v2/audit/trail").param("type", "PURPOSE").param("id", String.valueOf(id)).with(user(stranger)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v2/audit/activity").param("category", "purpose").with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].targetId").value(String.valueOf(id)));
    }

    @Test
    void refusedAndInvalidAttemptsAreRecordedToo() throws Exception {
        TestPrincipal school = TestPrincipal.of(900_204, Role.SCHOOL, organisation("Audit test school", "SCHOOL"));
        mvc.perform(post("/api/v2/match-runs").with(user(school)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"purposeIds\":[1]}"))
                .andExpect(status().isForbidden());
        assertThat(db.sql("select outcome from audit_event where actor_id = 900204 and action = 'match.run'").query(String.class).single())
                .isEqualTo("DENIED");

        long donorOrg = organisation("Audit test donor 2", "DONOR_INDIVIDUAL");
        TestPrincipal donor = TestPrincipal.of(900_205, Role.DONOR_INDIVIDUAL, donorOrg);
        mvc.perform(post("/api/v2/purposes").with(user(donor)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(db.sql("select error_code from audit_event where actor_id = 900205 and action = 'purpose.create'").query(String.class).single())
                .isEqualTo("invalid-input");

        mvc.perform(get("/api/v2/audit/events").param("outcome", "DENIED").with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].ip").exists());
        mvc.perform(get("/api/v2/audit/events").with(user(donor))).andExpect(status().isForbidden());
    }

    @Test
    void nothingRecordedCanBeChangedOrRemoved() {
        db.sql("insert into audit_event (action, category, outcome) values ('auth.sign-in', 'auth', 'SUCCESS')").update();
        assertThatThrownBy(() -> db.sql("update audit_event set detail = 'changed'").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.sql("delete from audit_event").update()).hasMessageContaining("append-only");
    }
}
