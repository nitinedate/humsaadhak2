package org.humsaadhak.identity;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Runs without Docker (SignInIntegrationTests needs it): HttpServletRequest.changeSessionId() throws when there is no
 * session, which is every first sign-in, so it may only be called when a session exists.
 */
class SignInSessionGuardTest {
    @Test
    void theSessionIdIsOnlyChangedWhenASessionExists() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/humsaadhak/identity/controller/AuthController.java"));
        int calls = 0;
        for (String line : src.split("\n")) {
            if (line.contains(".changeSessionId()")) {
                calls++;
                assertThat(line.contains("getSession(false) != null")).isTrue();
            }
        }
        assertThat(calls).isEqualTo(1);
        assertThat(src.contains("else req.getSession(true);")).isTrue();
    }
}
