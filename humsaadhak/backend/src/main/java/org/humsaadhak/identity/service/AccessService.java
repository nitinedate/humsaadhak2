package org.humsaadhak.identity.service;

import org.humsaadhak.shared.security.AuditContext;

import java.util.*;
import org.humsaadhak.identity.dao.AccessDao;
import org.humsaadhak.identity.dto.ChangeView;
import org.humsaadhak.identity.dto.KindOption;
import org.humsaadhak.identity.dto.PermissionView;
import org.humsaadhak.identity.dto.RoleView;
import org.humsaadhak.identity.util.RolePolicy;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles and permissions: rules (RolePolicy), the change log, and signing out the people a change affects so their new
 * rights apply at once. Storage is in AccessDao.
 */
@Service
@Transactional
public class AccessService {
    private final AccessDao access;
    private final SessionRevoker sessions;

    public AccessService(AccessDao access, SessionRevoker sessions) { this.access = access; this.sessions = sessions; }

    // ------------------------------------------------------------------ used at sign-in
    @Transactional(readOnly = true)
    public Set<String> permissionsOf(String roleCode) { return access.permissionsOf(roleCode); }

    @Transactional(readOnly = true)
    public String roleName(String roleCode) { return access.roleName(roleCode).orElse(roleCode); }

    @Transactional(readOnly = true)
    public Optional<Role> kindOf(String roleCode) { return access.roleKind(roleCode).map(Role::valueOf); }

    // ------------------------------------------------------------------ roles
    @Transactional(readOnly = true)
    public List<RoleView> roles() {
        Map<String, List<String>> grants = access.grantsByRole();
        return access.roles().stream().map(r -> {
            Role kind = Role.valueOf(r.kind());
            return new RoleView(r.code(), r.name(), r.description(), kind, RolePolicy.kindLabel(kind), r.system(), r.users(), grants.getOrDefault(r.code(), List.of()));
        }).toList();
    }

    public RoleView createRole(String name, String description, String kind, String copyFrom, CurrentUser me) {
        Role k = RolePolicy.customKind(kind);
        String code = RolePolicy.newRoleCode(name, access.roleCodes());
        if (access.roleNameTaken(name.trim(), null)) throw new DomainException("duplicate", "A role called “" + name.trim() + "” already exists.");
        access.insertRole(code, name.trim(), blankToNull(description), k.name(), me.email());
        boolean copy = copyFrom != null && !copyFrom.isBlank();
        if (copy) {
            role(copyFrom);   // must exist
            access.copyGrants(copyFrom, code);
        }
        logChange(me.email(), "ROLE_CREATED", name.trim(), "Account type: " + RolePolicy.kindLabel(k) + (copy ? "; permissions copied from " + roleName(copyFrom) : ""));
        return role(code);
    }

    public RoleView updateRole(String code, String name, String description, Set<String> permissions, CurrentUser me) {
        RoleView current = role(code);
        if (name != null && !name.trim().equals(current.name())) {
            RolePolicy.checkRename(current.system());
            if (access.roleNameTaken(name.trim(), code)) throw new DomainException("duplicate", "A role called “" + name.trim() + "” already exists.");
            access.renameRole(code, name.trim());
        }
        access.describeRole(code, blankToNull(description));
        Set<String> before = new HashSet<>(current.permissions());
        Map<String, String> names = access.permissionNames();
        RolePolicy.checkPermissions(code, permissions, names.keySet());
        if (!before.equals(permissions)) {
            access.replaceGrants(code, permissions);
            logChange(me.email(), "ROLE_PERMISSIONS_CHANGED", roleName(code), RolePolicy.describeChange(before, permissions, names));
            sessions.revoke(access.emailsWithRole(code));
        }
        return role(code);
    }

    public void deleteRole(String code, CurrentUser me) {
        RoleView r = role(code);
        RolePolicy.checkDeleteRole(r.system(), r.users());
        access.deleteRole(code);
        logChange(me.email(), "ROLE_DELETED", r.name(), null);
    }

    // ------------------------------------------------------------------ permissions
    @Transactional(readOnly = true)
    public List<PermissionView> permissions() {
        Map<String, List<String>> roles = access.roleNamesByPermission();
        return access.permissions().stream()
                .map(p -> new PermissionView(p.code(), p.name(), p.description(), p.category(), p.system(), roles.getOrDefault(p.code(), List.of()))).toList();
    }

    public PermissionView createPermission(String code, String name, String description, String category, CurrentUser me) {
        String c = RolePolicy.newPermissionCode(code, access.permissionNames().keySet());
        if (name == null || name.isBlank()) throw new DomainException("name-required", "Give the permission a name people will understand.");
        access.insertPermission(c, name.trim(), blankToNull(description), categoryOrDefault(category), me.email());
        logChange(me.email(), "PERMISSION_CREATED", c, name.trim());
        return permission(c);
    }

    public PermissionView updatePermission(String code, String name, String description, String category, CurrentUser me) {
        RolePolicy.checkEditPermission(permission(code).system());
        if (name == null || name.isBlank()) throw new DomainException("name-required", "Give the permission a name people will understand.");
        access.updatePermission(code, name.trim(), blankToNull(description), categoryOrDefault(category));
        logChange(me.email(), "PERMISSION_CHANGED", code, name.trim());
        return permission(code);
    }

    public void deletePermission(String code, CurrentUser me) {
        PermissionView p = permission(code);
        RolePolicy.checkEditPermission(p.system());
        List<String> affected = access.emailsWithPermission(code);
        access.deletePermission(code);
        logChange(me.email(), "PERMISSION_DELETED", code, p.roles().isEmpty() ? null : "Removed from: " + String.join(", ", p.roles()));
        sessions.revoke(affected);
    }

    // ------------------------------------------------------------------ other
    public static List<KindOption> kinds() {
        return RolePolicy.CUSTOM_ROLE_KINDS.entrySet().stream().map(e -> new KindOption(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeView> changes() { return access.recentChanges(50); }

    /** Records a change made elsewhere (e.g. a person's role changed on the Users screen). */
    public void log(CurrentUser me, String action, String subject, String details) { logChange(me.email(), action, subject, details); }

    public RoleView role(String code) {
        return roles().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow(() -> new NotFoundException("Role", code));
    }

    private PermissionView permission(String code) {
        return permissions().stream().filter(p -> p.code().equals(code)).findFirst().orElseThrow(() -> new NotFoundException("Permission", code));
    }

    private static String categoryOrDefault(String category) { return category == null || category.isBlank() ? "Custom" : category.trim(); }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    /** Writes the access change log and adds the same details to this request's audit record. */
    private void logChange(String actor, String action, String subject, String details) {
        access.log(actor, action, subject, details);
        AuditContext.note(details);
    }
}
