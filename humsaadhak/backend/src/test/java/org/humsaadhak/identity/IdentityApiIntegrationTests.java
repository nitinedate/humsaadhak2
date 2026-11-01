package org.humsaadhak.identity;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.service.AppUserDetailsService;
import org.humsaadhak.identity.repository.AppUserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.humsaadhak.TestcontainersConfiguration;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Boots the whole application against PostgreSQL (Docker required) and exercises the HTTP layer with
 * Spring Security in place: sessions, CSRF, permissions and the new endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IdentityApiIntegrationTests {

    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Autowired AppUserDetailsService details;

    /** Signs in through the real user loader, so permissions come from the role tables exactly as at login. */
    private AppUserDetailsService.AppUserDetails signedIn(Role role, String email) {
        AppUser u = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppUser n = new AppUser(email, "Test " + role, null, role, null);
            n.activate();
            return users.save(n);
        });
        return (AppUserDetailsService.AppUserDetails) details.loadUserByUsername(u.getEmail());
    }

    @Test
    void apiNeedsASession() throws Exception {
        mvc.perform(get("/api/v2/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v2/reference/sectors")).andExpect(status().isOk());
    }

    @Test
    void themeIsSavedOnTheAccount() throws Exception {
        var me = signedIn(Role.DONOR_INDIVIDUAL, "theme@test.humsaadhak.org");
        mvc.perform(get("/api/v2/me/preferences").with(user(me))).andExpect(jsonPath("$.theme").value("INDIGO"));
        mvc.perform(put("/api/v2/me/preferences").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"NIGHT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.theme").value("NIGHT"));
        mvc.perform(get("/api/v2/me").with(user(me))).andExpect(jsonPath("$.theme").value("NIGHT"));
    }

    @Test
    void writesWithoutCsrfTokenAreRefused() throws Exception {
        var me = signedIn(Role.DONOR_INDIVIDUAL, "csrf@test.humsaadhak.org");
        mvc.perform(put("/api/v2/me/preferences").with(user(me)).contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"OCEAN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownThemeIsABadRequest() throws Exception {
        var me = signedIn(Role.DONOR_INDIVIDUAL, "bad-theme@test.humsaadhak.org");
        mvc.perform(put("/api/v2/me/preferences").with(user(me)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"PINK\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlySuperAdminsManageUsersAndNobodyDisablesThemselves() throws Exception {
        var donor = signedIn(Role.DONOR_CORPORATE, "donor-admin@test.humsaadhak.org");
        mvc.perform(get("/api/v2/admin/users").with(user(donor))).andExpect(status().isForbidden());

        var admin = signedIn(Role.SUPER_ADMIN, "admin@test.humsaadhak.org");
        mvc.perform(get("/api/v2/admin/users").param("q", "donor-admin").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].email").value("donor-admin@test.humsaadhak.org"));
        mvc.perform(post("/api/v2/admin/users/{id}/disable", admin.id()).with(user(admin)).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("own-account"));
        mvc.perform(post("/api/v2/admin/users/{id}/disable", donor.id()).with(user(admin)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void importTemplateDownloadsForImporters() throws Exception {
        var associate = signedIn(Role.ASSOCIATE, "associate@test.humsaadhak.org");
        mvc.perform(get("/api/v2/imports/templates/NEEDS").with(user(associate)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("needs-template.xlsx")));
        var school = signedIn(Role.SCHOOL, "school-import@test.humsaadhak.org");
        mvc.perform(get("/api/v2/imports/templates/NEEDS").with(user(school))).andExpect(status().isForbidden());
    }

    @Test
    void historyRejectsUnknownTypes() throws Exception {
        var manager = signedIn(Role.MANAGER, "manager-history@test.humsaadhak.org");
        mvc.perform(get("/api/v2/history/purpose/999999").with(user(manager))).andExpect(status().isOk());
        mvc.perform(get("/api/v2/history/secrets/1").with(user(manager))).andExpect(status().isNotFound());
    }
}
