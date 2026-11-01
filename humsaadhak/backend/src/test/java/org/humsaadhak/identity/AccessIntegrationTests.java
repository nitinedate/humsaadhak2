package org.humsaadhak.identity;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.service.AppUserDetailsService;
import org.humsaadhak.identity.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

/** Roles and permissions end to end against PostgreSQL (Docker): custom roles, grants, sign-out on change, protections. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccessIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired AppUserDetailsService details;
    @Autowired JdbcClient db;
    MockMvc mvc;
    final TestPrincipal admin = TestPrincipal.of(990_001, Role.SUPER_ADMIN, null);

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build(); }

    private String json(String body) { return body.replace('\'', '"'); }

    @Test
    void aCustomRoleWithACustomPermissionReachesThePersonAtTheirNextSignIn() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        // a unique, valid code: digits become letters (codes are lower-case words joined by dots)
        String perm = "fieldvisits" + suffix.chars().mapToObj(c -> String.valueOf((char) ('a' + (c - '0')))).reduce("", String::concat) + ".approve";
        mvc.perform(post("/api/v2/admin/access/permissions").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'code':'" + perm + "','name':'Approve field visits','category':'Field work'}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.system").value(false));

        String roleName = "Regional coordinator " + suffix;
        String code = mvc.perform(post("/api/v2/admin/access/roles").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'" + roleName + "','kind':'ASSOCIATE','copyFrom':'ASSOCIATE'}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.permissions", hasItem("purpose.edit")))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"code\":\"([A-Z0-9_]+)\".*", "$1");

        AppUser person = new AppUser("coordinator" + suffix + "@test.humsaadhak.org", "Coordinator", null, Role.ASSOCIATE, null);
        person.activate();
        users.save(person);
        mvc.perform(put("/api/v2/admin/users/{id}/role", person.getId()).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'roleCode':'" + code + "'}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value(roleName));

        // a session the person already has is ended when the role's permissions change
        db.sql("insert into spring_session values ('s" + suffix + "', 'x" + suffix + "', 0, 0, 1800, 9999999999999, ?)").param(person.getEmail()).update();
        mvc.perform(put("/api/v2/admin/access/roles/{code}", code).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'description':'Coordinates a region','permissions':['purpose.edit','" + perm + "']}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions", containsInAnyOrder("purpose.edit", perm)));
        assertThat(db.sql("select count(*) from spring_session where principal_name = ?").param(person.getEmail()).query(Long.class).single()).isZero();

        var signedIn = (AppUserDetailsService.AppUserDetails) details.loadUserByUsername(person.getEmail());
        assertThat(signedIn.permissions()).containsExactlyInAnyOrder("purpose.edit", perm);
        assertThat(signedIn.roleName()).isEqualTo(roleName);
        assertThat(signedIn.getAuthorities().stream().map(Object::toString).toList()).contains("perm:" + perm, "ROLE_ASSOCIATE");

        mvc.perform(delete("/api/v2/admin/access/roles/{code}", code).with(user(admin)).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("in-use"));
        mvc.perform(get("/api/v2/admin/access/changes").with(user(admin)))
                .andExpect(jsonPath("$[*].action", hasItems("PERMISSION_CREATED", "ROLE_CREATED", "USER_ROLE_CHANGED", "ROLE_PERMISSIONS_CHANGED")));
    }

    @Test
    void protections() throws Exception {
        mvc.perform(put("/api/v2/admin/access/roles/SUPER_ADMIN").with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'permissions':['admin.users']}")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("lockout"));
        mvc.perform(delete("/api/v2/admin/access/roles/MANAGER").with(user(admin)).with(csrf()))
                .andExpect(jsonPath("$.code").value("system-role"));
        mvc.perform(delete("/api/v2/admin/access/permissions/record.approve").with(user(admin)).with(csrf()))
                .andExpect(jsonPath("$.code").value("system-permission"));

        var manager = TestPrincipal.of(990_002, Role.MANAGER, null);
        mvc.perform(post("/api/v2/admin/access/roles").with(user(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("{'name':'Sneaky','kind':'MANAGER'}"))).andExpect(status().isForbidden());

        // a person in charge of users (but not roles) can still read the role list for the Users screen
        var usersOnly = new TestPrincipal(new org.humsaadhak.shared.security.CurrentUser(990_003, "u@test.humsaadhak.org", "U", null, Role.ASSOCIATE,
                "X", "X", java.util.Set.of("admin.users")));
        mvc.perform(get("/api/v2/admin/access/roles").with(user(usersOnly))).andExpect(status().isOk());
        mvc.perform(get("/api/v2/admin/access/permissions").with(user(usersOnly))).andExpect(status().isForbidden());
    }

    @Test
    void aRoleMustFitThePersonsAccount() throws Exception {
        long org = db.sql("insert into organisation(created_at, name, type) values (now(), 'Fit test NGO', 'NGO') returning id").query(Long.class).single();
        AppUser ngoUser = new AppUser("fit" + System.nanoTime() + "@test.humsaadhak.org", "NGO person", null, Role.NGO, org);
        ngoUser.activate();
        users.save(ngoUser);
        mvc.perform(put("/api/v2/admin/users/{id}/role", ngoUser.getId()).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'roleCode':'MANAGER'}")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("role-does-not-fit"));
    }
}
