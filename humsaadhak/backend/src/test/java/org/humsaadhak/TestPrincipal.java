package org.humsaadhak;

import java.util.Collection;
import java.util.stream.Stream;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Permission;
import org.humsaadhak.shared.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** A signed-in person for MockMvc tests in any module, with the same authorities the real login grants. */
public record TestPrincipal(CurrentUser currentUser) implements UserDetails, CurrentUser.Principal {
    public static TestPrincipal of(long id, Role role, Long orgId) {
        return new TestPrincipal(new CurrentUser(id, role.name().toLowerCase() + id + "@test.humsaadhak.org", "Test " + role, orgId, role,
                role.name(), role.name(), role.defaultPermissions().stream().map(Permission::code).collect(java.util.stream.Collectors.toSet())));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(Stream.of("ROLE_" + currentUser.role().name()), currentUser.permissions().stream().map(p -> "perm:" + p))
                .map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return currentUser.email(); }
}
