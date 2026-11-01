package org.humsaadhak.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Super admins add staff (who never sign up themselves); the person gets an invitation link (PostgreSQL in Docker). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StaffAccountsIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient db;
    MockMvc mvc;
    final TestPrincipal admin = TestPrincipal.of(900_301, Role.SUPER_ADMIN, null);

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build(); }

    static String body(String email, String role) {
        return "{\"fullName\":\"Anita Deshpande\",\"email\":\"" + email + "\",\"roleCode\":\"" + role + "\"}";
    }

    @Test
    void aStaffMemberIsCreatedActiveWithoutAPasswordAndInvited() throws Exception {
        mvc.perform(post("/api/v2/admin/users").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("anita.staff@example.org", "ASSOCIATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("ASSOCIATE"))
                .andExpect(jsonPath("$.hasPassword").value(false));
        long id = db.sql("select id from app_user where email = 'anita.staff@example.org'").query(Long.class).single();
        long tokens = 0;   // the invitation is issued by a listener after the commit: give it a moment
        for (int i = 0; i < 50 && tokens == 0; i++) {
            tokens = db.sql("select count(*) from one_time_token where user_id = ? and purpose = 'INVITATION'").param(id).query(Long.class).single();
            if (tokens == 0) Thread.sleep(100);
        }
        assertThat(tokens).isEqualTo(1L);
        assertThat(db.sql("select action from audit_event where target_type = 'USER' and target_id = ?").param(String.valueOf(id))
                .query(String.class).list()).contains("user.create");
    }

    @Test
    void onlyStaffRolesAndUnusedEmailsAndSuperAdmins() throws Exception {
        mvc.perform(post("/api/v2/admin/users").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("donor.role@example.org", "DONOR_INDIVIDUAL")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("role-does-not-fit"));
        mvc.perform(post("/api/v2/admin/users").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("twice@example.org", "MANAGER")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v2/admin/users").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("TWICE@example.org", "MANAGER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("email-taken"));
        TestPrincipal manager = TestPrincipal.of(900_302, Role.MANAGER, null);
        mvc.perform(post("/api/v2/admin/users").with(user(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("x@example.org", "ASSOCIATE")))
                .andExpect(status().isForbidden());
    }
}
