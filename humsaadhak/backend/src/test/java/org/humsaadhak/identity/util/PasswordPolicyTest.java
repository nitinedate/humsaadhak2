package org.humsaadhak.identity.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void acceptsALongMixedPassword() {
        assertThat(PasswordPolicy.problems("river-lamp-42-mango", "meera@asha.org").isEmpty()).isTrue();
    }

    @Test
    void explainsEveryProblem() {
        assertThat(PasswordPolicy.problems("short", null)).isEqualTo(List.of("Use at least 12 characters.", "Add a number or a symbol."));
        assertThat(PasswordPolicy.problems("meera.iyer-2026!", "meera.iyer@asha.org")).isEqualTo(List.of("Do not use your email name."));
        assertThat(PasswordPolicy.problems(null, null).size()).isEqualTo(2);
    }

    @Test
    void veryShortEmailNamesAreIgnored() {
        assertThat(PasswordPolicy.problems("ab-river-lamp-42", "ab@x.org").isEmpty()).isTrue();
    }
}
