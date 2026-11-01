package org.humsaadhak.identity.util;

import java.util.*;
import java.util.regex.Pattern;
import org.humsaadhak.shared.util.Codes;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.security.Role;

/**
 * Rules for administering roles and permissions (pure, unit-tested):
 *  - system roles and permissions come from the application and cannot be deleted or renamed;
 *  - the Super admin role always keeps "Manage users" and "Manage roles and permissions", so nobody can lock everyone out;
 *  - a role in use cannot be deleted; a custom role has an account type that matches the people who will hold it;
 *  - custom permission codes look like "reports.view" (lower case, dot separated) and never reuse a code.
 */
public final class RolePolicy {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final Set<String> SUPER_ADMIN_KEEPS = Set.of("admin.users", "admin.roles");
    private static final Pattern PERMISSION_CODE = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    /** Account types a new role can have, with the words people use for them. */
    public static final Map<Role, String> CUSTOM_ROLE_KINDS = new LinkedHashMap<>();
    static {
        CUSTOM_ROLE_KINDS.put(Role.MANAGER, "HumSaadhak staff, platform overview dashboard");
        CUSTOM_ROLE_KINDS.put(Role.ASSOCIATE, "HumSaadhak staff, work dashboard");
        CUSTOM_ROLE_KINDS.put(Role.DONOR_INDIVIDUAL, "Individual donor");
        CUSTOM_ROLE_KINDS.put(Role.DONOR_CORPORATE, "Corporate donor");
        CUSTOM_ROLE_KINDS.put(Role.SCHOOL, "Direct beneficiary (school)");
        CUSTOM_ROLE_KINDS.put(Role.NGO, "Indirect beneficiary (NGO)");
    }

    private RolePolicy() {}

    public static String newRoleCode(String name, Set<String> existingCodes) {
        String code = Codes.fromName(name);
        if (existingCodes.contains(code)) throw new DomainException("duplicate", "A role called “" + name.trim() + "” already exists.");
        return code;
    }

    public static Role customKind(String kind) {
        try {
            Role r = Role.valueOf(kind);
            if (CUSTOM_ROLE_KINDS.containsKey(r)) return r;
        } catch (RuntimeException ignored) { }
        throw new DomainException("bad-kind", "Choose the type of account for this role.");
    }

    public static String newPermissionCode(String code, Set<String> existingCodes) {
        String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (c.length() > 80 || !PERMISSION_CODE.matcher(c).matches())
            throw new DomainException("bad-code", "A permission code looks like reports.view: lower case words joined by dots.");
        if (existingCodes.contains(c)) throw new DomainException("duplicate", "The permission " + c + " already exists.");
        return c;
    }

    /** Checks a new permission set for a role; unknown codes and lock-outs are refused. */
    public static void checkPermissions(String roleCode, Set<String> wanted, Set<String> knownPermissions) {
        List<String> unknown = wanted.stream().filter(p -> !knownPermissions.contains(p)).sorted().toList();
        if (!unknown.isEmpty()) throw new DomainException("unknown-permission", "Unknown permission " + String.join(", ", unknown) + ".");
        if (SUPER_ADMIN.equals(roleCode) && !wanted.containsAll(SUPER_ADMIN_KEEPS))
            throw new DomainException("lockout", "The Super admin role always keeps “Manage users” and “Manage roles and permissions”, so the platform can always be administered.");
    }

    public static void checkRename(boolean system) {
        if (system) throw new DomainException("system-role", "Built-in roles keep their names. Create a new role instead.");
    }

    public static void checkDeleteRole(boolean system, long usersWithRole) {
        if (system) throw new DomainException("system-role", "Built-in roles cannot be deleted.");
        if (usersWithRole > 0)
            throw new DomainException("in-use", usersWithRole + (usersWithRole == 1 ? " person has" : " people have") + " this role. Give them another role first.");
    }

    public static void checkEditPermission(boolean system) {
        if (system) throw new DomainException("system-permission", "Built-in permissions are part of the application and cannot be changed or deleted.");
    }

    /** "Added: Approve records; Removed: Bulk import" for the change log. */
    public static String describeChange(Set<String> before, Set<String> after, Map<String, String> names) {
        List<String> added = after.stream().filter(p -> !before.contains(p)).map(p -> names.getOrDefault(p, p)).sorted().toList();
        List<String> removed = before.stream().filter(p -> !after.contains(p)).map(p -> names.getOrDefault(p, p)).sorted().toList();
        List<String> parts = new ArrayList<>();
        if (!added.isEmpty()) parts.add("Added: " + String.join(", ", added));
        if (!removed.isEmpty()) parts.add("Removed: " + String.join(", ", removed));
        return parts.isEmpty() ? "No permission changes" : String.join("; ", parts);
    }

    /** The words people use for each account type. */
    public static String kindLabel(Role kind) {
        return switch (kind) {
            case SUPER_ADMIN -> "Super admin";
            case MANAGER -> "HumSaadhak staff, platform overview dashboard";
            case ASSOCIATE -> "HumSaadhak staff, work dashboard";
            case DONOR_INDIVIDUAL -> "Individual donor";
            case DONOR_CORPORATE -> "Corporate donor";
            case SCHOOL -> "Direct beneficiary (school)";
            case NGO -> "Indirect beneficiary (NGO)";
            case GUEST -> "Guest";
        };
    }

    /**
     * Whether someone whose organisation is of orgType (null for staff) may hold a role of this kind:
     * staff roles need no organisation; donor, school and NGO roles need an organisation of that type.
     */
    public static boolean fits(Role kind, String orgType) {
        if (kind.isStaff()) return orgType == null;
        if (kind == Role.GUEST) return true;
        return kind.name().equals(orgType);
    }
}
