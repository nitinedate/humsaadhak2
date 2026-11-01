package org.humsaadhak.documents;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;
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

/** Who sees which documents: staff with the review permission see all (filtered); everyone else only their own. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentListIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    final String tag = "Doclist" + System.nanoTime();
    final TestPrincipal manager = TestPrincipal.of(910_001, Role.MANAGER, null);
    long school, ngo, a12, schoolPan, ngo12a, ngo80g;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        school = org(tag + " Alpha School", "SCHOOL");
        ngo = org(tag + " Beta NGO", "NGO");
        long pan = type("PAN"), g80 = type("CERT_80G");
        a12 = type("CERT_12A");
        schoolPan = doc(school, pan, "pan.pdf", "REVIEWED", null, false, "reviewer@test");
        ngo12a = doc(ngo, a12, "12a.pdf", "PENDING", null, false, null);
        doc(ngo, g80, "80g-old.pdf", "REJECTED", "Expired", true, "reviewer@test");          // replaced: never listed
        ngo80g = doc(ngo, g80, "80g.pdf", "REJECTED", "Unreadable scan", false, "reviewer@test");
    }

    long org(String name, String type) {
        return db.sql("insert into organisation(created_at, version, name, type, verified) values (now(), 0, :n, :t, false) returning id")
                .param("n", name).param("t", type).query(Long.class).single();
    }

    long type(String code) { return db.sql("select id from document_type where code = :c").param("c", code).query(Long.class).single(); }

    long doc(long owner, long type, String name, String state, String reason, boolean superseded, String reviewer) {
        return db.sql("""
                insert into document(created_at, created_by, updated_at, updated_by, version, owner_org_id, document_type_id, storage_key,
                                     original_name, content_type, size_bytes, doc_version, state, rejection_reason, superseded)
                values (now(), 'uploader@test', :reviewedAt, :reviewer, 0, :owner, :type, :key, :name, 'application/pdf', 100, 1, :state, :reason, :superseded)
                returning id""")
                .param("reviewedAt", reviewer == null ? null : Timestamp.from(Instant.now())).param("reviewer", reviewer)
                .param("owner", owner).param("type", type).param("key", tag + "/" + owner + "/" + name).param("name", name)
                .param("state", state).param("reason", reason).param("superseded", superseded)
                .query(Long.class).single();
    }

    @Test
    void aManagerSeesEveryOrganisationsCurrentDocumentsAndFiltersThem() throws Exception {
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag).with(user(manager))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("pan.pdf", "12a.pdf", "80g.pdf")));    // not 80g-old.pdf
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag).param("state", "REVIEWED").with(user(manager)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("pan.pdf"))
                .andExpect(jsonPath("$.items[0].ownerName").value(tag + " Alpha School"))
                .andExpect(jsonPath("$.items[0].reviewedBy").value("reviewer@test"));
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag).param("state", "REJECTED").with(user(manager)))
                .andExpect(jsonPath("$.items[*].name", contains("80g.pdf")))
                .andExpect(jsonPath("$.items[0].rejectionReason").value("Unreadable scan"));
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag).param("state", "PENDING").with(user(manager)))
                .andExpect(jsonPath("$.items[*].name", contains("12a.pdf")))
                .andExpect(jsonPath("$.items[0].reviewedBy").doesNotExist());
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag).param("typeId", String.valueOf(a12)).with(user(manager)))
                .andExpect(jsonPath("$.items[*].name", contains("12a.pdf")));
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag + " beta").with(user(manager)))        // any case, part of the name
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v2/documents/all").param("organisation", tag + " nobody").with(user(manager)))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v2/documents/all").param("state", "SOMETHING").with(user(manager)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("invalid-state"));
    }

    @Test
    void nobodyElseCanListAllDocuments() throws Exception {
        mvc.perform(get("/api/v2/documents/all").with(user(TestPrincipal.of(910_002, Role.SCHOOL, school)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/documents/all").with(user(TestPrincipal.of(910_003, Role.NGO, ngo)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/documents/all").with(user(TestPrincipal.of(910_004, Role.DONOR_CORPORATE, null)))).andExpect(status().isForbidden());
    }

    @Test
    void aSchoolSeesOnlyItsOwnDocumentsEvenWhenAskingForAnothers() throws Exception {
        TestPrincipal schoolUser = TestPrincipal.of(910_005, Role.SCHOOL, school);
        mvc.perform(get("/api/v2/documents").param("ownerOrgId", String.valueOf(ngo)).with(user(schoolUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) schoolPan)));
        mvc.perform(get("/api/v2/documents/{id}/download-url", ngo12a).with(user(schoolUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/documents/{id}/download-url", ngo80g).with(user(schoolUser))).andExpect(status().isForbidden());
    }

    @Test
    void staffWithoutAnOrganisationGetAnEmptyOwnListNotAnError() throws Exception {
        mvc.perform(get("/api/v2/documents").with(user(manager))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
