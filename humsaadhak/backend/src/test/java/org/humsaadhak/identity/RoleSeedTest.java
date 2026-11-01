package org.humsaadhak.identity;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.humsaadhak.shared.security.Permission;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.Test;

/** The seeds (V6, plus permissions added by later migrations) must give every built-in role exactly its permissions in code. */
class RoleSeedTest {
    static String seed() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V6__roles_permissions.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/V7__audit_trail.sql"));
    }

    @Test
    void builtInRolesKeepTheirPermissions() throws Exception {
        String sql = seed();
        Map<String, Set<String>> seeded = new HashMap<>();
        // every grant statement, in V6 and in later migrations
        for (int at = sql.indexOf("insert into app_role_permission"); at >= 0; at = sql.indexOf("insert into app_role_permission", at + 1)) {
            Matcher m = Pattern.compile("\\('([A-Z_]+)', '([a-z.]+)'\\)").matcher(sql.substring(at, sql.indexOf(";", at)));
            while (m.find()) seeded.computeIfAbsent(m.group(1), k -> new HashSet<>()).add(m.group(2));
        }
        for (Role r : Role.values()) {
            Set<String> expected = new HashSet<>();
            r.defaultPermissions().forEach(p -> expected.add(p.code()));
            assertThat(seeded.getOrDefault(r.name(), Set.of())).isEqualTo(expected);
        }
    }

    @Test
    void everyBuiltInPermissionIsSeeded() throws Exception {
        String sql = seed();
        for (Permission p : Permission.values()) assertThat(sql.contains("('" + p.code() + "', '")).isTrue();
    }
}
