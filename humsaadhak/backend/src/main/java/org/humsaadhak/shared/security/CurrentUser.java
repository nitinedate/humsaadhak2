package org.humsaadhak.shared.security;

import java.io.Serializable;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The signed-in person, as every module sees them.
 * @param role        account type (staff, donor, school, NGO): decides data scope and dashboard
 * @param roleCode    the assigned role (system or custom), e.g. MANAGER or REGIONAL_COORDINATOR
 * @param permissions permission codes granted by that role, e.g. record.approve
 */
public record CurrentUser(long id, String email, String fullName, Long orgId, Role role, String roleCode, String roleName,
                          Set<String> permissions) implements Serializable {

    public boolean can(Permission p) { return permissions.contains(p.code()); }
    public boolean can(String permissionCode) { return permissions.contains(permissionCode); }

    public static CurrentUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Principal p) return p.currentUser();
        throw new IllegalStateException("No signed-in user");
    }

    /** Implemented by the identity module's UserDetails so other modules never see it. */
    public interface Principal { CurrentUser currentUser(); }
}
