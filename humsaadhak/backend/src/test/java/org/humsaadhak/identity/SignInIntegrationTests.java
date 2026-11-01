package org.humsaadhak.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.humsaadhak.TestcontainersConfiguration;
import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The real sign-in path, POST /auth/login (the other tests sign in with test helpers). The first case is every
 * browser's first sign-in: no session yet, because the CSRF token lives in a cookie. It answered 500 when the
 * controller changed the session id unconditionally (changeSessionId() throws without a session).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SignInIntegrationTests {
    static final String PASSWORD = "a long enough password";
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build(); }

    String activeUser() {
        String email = "signin." + System.nanoTime() + "@example.org";
        AppUser u = new AppUser(email, "Sign-in Test", null, Role.MANAGER, null);
        u.activate();
        u.setPasswordHash(encoder.encode(PASSWORD));
        users.save(u);
        return email;
    }

    static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void theFirstSignInWithoutAnySessionWorksAndStartsOne() throws Exception {
        String email = activeUser();
        MvcResult r = mvc.perform(post("/api/v2/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/v2/me").session(session))                      // the new session is signed in
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void signingInWithAnExistingSessionGivesItANewId() throws Exception {      // against session fixation
        String email = activeUser();
        MockHttpSession existing = new MockHttpSession();
        String before = existing.getId();
        MvcResult r = mvc.perform(post("/api/v2/auth/login").session(existing).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(r.getRequest().getSession(false).getId()).isNotEqualTo(before);
    }

    @Test
    void aWrongPasswordIsRefusedNotAServerError() throws Exception {
        String email = activeUser();
        mvc.perform(post("/api/v2/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(email, "not the password")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("bad-credentials"));
    }
}
