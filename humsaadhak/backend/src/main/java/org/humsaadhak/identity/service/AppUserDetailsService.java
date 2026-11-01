package org.humsaadhak.identity.service;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.identity.model.UserStatus;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a person for sign-in with the permissions of their assigned role, read from the database (AccessService).
 * The principal is a small serialisable snapshot (never the JPA entity), because Spring Session stores it in the database.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private final AccessService access;
    private final Clock clock;

    public AppUserDetailsService(AppUserRepository users, AccessService access, Clock clock) {
        this.users = users; this.access = access; this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser u = users.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("unknown"));
        return AppUserDetails.of(u, access.roleName(u.getRoleCode()), access.permissionsOf(u.getRoleCode()), Instant.now(clock));
    }

    public record AppUserDetails(long id, String email, String fullName, Long orgId, Role kind, String roleCode, String roleName,
                          Set<String> permissions, String passwordHash, boolean locked, boolean enabled)
            implements UserDetails, CurrentUser.Principal, Serializable {

        public static AppUserDetails of(AppUser u, String roleName, Set<String> permissions, Instant now) {
            return new AppUserDetails(u.getId(), u.getEmail(), u.getFullName(), u.getOrgId(), u.getRole(), u.getRoleCode(), roleName,
                    Set.copyOf(permissions), u.getPasswordHash(), u.isLocked(now),
                    u.getStatus() == UserStatus.ACTIVE && u.getPasswordHash() != null);
        }

        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            List<GrantedAuthority> list = new ArrayList<>();
            list.add(new SimpleGrantedAuthority("ROLE_" + kind.name()));
            for (String p : permissions) list.add(new SimpleGrantedAuthority("perm:" + p));
            return list;
        }
        @Override public String getPassword() { return passwordHash; }
        @Override public String getUsername() { return email; }
        @Override public boolean isAccountNonLocked() { return !locked; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public CurrentUser currentUser() { return new CurrentUser(id, email, fullName, orgId, kind, roleCode, roleName, permissions); }
    }
}
