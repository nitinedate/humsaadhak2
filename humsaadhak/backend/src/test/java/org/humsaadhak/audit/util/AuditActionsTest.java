package org.humsaadhak.audit.util;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.humsaadhak.audit.model.Outcome;
import org.junit.jupiter.api.Test;

class AuditActionsTest {
    @Test
    void everyActionUsedInAControllerHasAReadableName() throws Exception {
        List<String> unnamed = new ArrayList<>();
        int found = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/org/humsaadhak"))) {
            for (Path p : files.filter(f -> f.toString().endsWith("Controller.java")).toList()) {
                Matcher m = Pattern.compile("@AuditAction\\(value = \"([\\w.-]+)\"").matcher(Files.readString(p));
                while (m.find()) { found++; if (!AuditActions.known(m.group(1))) unnamed.add(m.group(1)); }
            }
        }
        assertThat(unnamed).isEmpty();
        assertThat(found >= 48).isTrue();
    }

    @Test
    void namesCategoriesAndOutcomes() {
        assertThat(AuditActions.label("purpose.update")).isEqualTo("Edited the purpose");
        assertThat(AuditActions.label("something.new")).isEqualTo("something.new");
        assertThat(AuditActions.category("document-type.create")).isEqualTo("document-type");
        assertThat(AuditActions.category("auth.sign-in")).isEqualTo("auth");
        assertThat(AuditActions.outcomeForStatus(403)).isEqualTo(Outcome.DENIED);
        assertThat(AuditActions.outcomeForStatus(401)).isEqualTo(Outcome.DENIED);
        assertThat(AuditActions.outcomeForStatus(400)).isEqualTo(Outcome.FAILED);
        assertThat(AuditActions.outcomeForStatus(500)).isEqualTo(Outcome.FAILED);
        assertThat(AuditActions.outcomeForStatus(201)).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void longTextIsClippedVisibly() {
        assertThat(AuditActions.clip("  short  ", 10)).isEqualTo("short");
        assertThat(AuditActions.clip("abcdefghijkl", 5)).isEqualTo("abcd…");
        assertThat(AuditActions.clip(null, 5)).isEqualTo(null);
    }

    @Test
    void ownersSeeTheirTrailsAndStaffSeeAll() {
        assertThat(AuditVisibility.canSeeTrail(true, null, java.util.Set.of())).isTrue();
        assertThat(AuditVisibility.canSeeTrail(false, 21L, java.util.Set.of(21L, 12L))).isTrue();
        assertThat(AuditVisibility.canSeeTrail(false, 31L, java.util.Set.of(21L))).isFalse();
        assertThat(AuditVisibility.canSeeTrail(false, null, java.util.Set.of(21L))).isFalse();
        assertThat(AuditVisibility.canSeeTrail(false, 21L, java.util.Set.of())).isFalse();   // staff-only records
        assertThat(AuditVisibility.seesEverything(false)).isFalse();
    }
}
