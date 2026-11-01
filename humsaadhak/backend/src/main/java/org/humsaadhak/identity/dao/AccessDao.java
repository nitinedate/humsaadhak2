package org.humsaadhak.identity.dao;

import java.util.*;
import java.util.stream.Collectors;
import org.humsaadhak.identity.dto.ChangeView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Roles, permissions, their grants and the access change log (tables from V6__roles_permissions.sql). */
@Repository
public class AccessDao {
    public record RoleRecord(String code, String name, String description, String kind, boolean system, long users) {}
    public record PermissionRecord(String code, String name, String description, String category, boolean system) {}

    private final JdbcClient db;

    public AccessDao(JdbcClient db) { this.db = db; }

    // ---------------------------------------------------------------- roles
    public List<RoleRecord> roles() {
        return db.sql("""
                select r.code, r.name, r.description, r.kind, r.system, (select count(*) from app_user u where u.role_code = r.code) as users
                from app_role r order by r.system desc, r.name""")
                .query((rs, i) -> new RoleRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getLong(6))).list();
    }
    public Optional<String> roleName(String code) { return db.sql("select name from app_role where code = ?").param(code).query(String.class).optional(); }
    public Optional<String> roleKind(String code) { return db.sql("select kind from app_role where code = ?").param(code).query(String.class).optional(); }
    public Set<String> roleCodes() { return new HashSet<>(db.sql("select code from app_role").query(String.class).list()); }
    /** Whether another role (not exceptCode) already uses this name, ignoring case. */
    public boolean roleNameTaken(String name, String exceptCode) {
        return db.sql("select count(*) from app_role where lower(name) = lower(?) and code <> ?").params(name, exceptCode == null ? "" : exceptCode)
                .query(Long.class).single() > 0;
    }
    public void insertRole(String code, String name, String description, String kind, String createdBy) {
        db.sql("insert into app_role(code, name, description, kind, system, created_by) values (?, ?, ?, ?, false, ?)")
                .params(code, name, description, kind, createdBy).update();
    }
    public void renameRole(String code, String name) { db.sql("update app_role set name = ? where code = ?").params(name, code).update(); }
    public void describeRole(String code, String description) {
        db.sql("update app_role set description = ?, updated_at = now() where code = ?").params(description, code).update();
    }
    public void deleteRole(String code) { db.sql("delete from app_role where code = ?").param(code).update(); }

    // ---------------------------------------------------------------- grants
    public Set<String> permissionsOf(String roleCode) {
        return new HashSet<>(db.sql("select permission_code from app_role_permission where role_code = ?").param(roleCode).query(String.class).list());
    }
    /** role code -> granted permission codes */
    public Map<String, List<String>> grantsByRole() {
        Map<String, List<String>> out = new HashMap<>();
        db.sql("select role_code, permission_code from app_role_permission order by permission_code")
                .query((rs, i) -> out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2))).list();
        return out;
    }
    /** permission code -> names of the roles that grant it */
    public Map<String, List<String>> roleNamesByPermission() {
        Map<String, List<String>> out = new HashMap<>();
        db.sql("select rp.permission_code, r.name from app_role_permission rp join app_role r on r.code = rp.role_code order by r.name")
                .query((rs, i) -> out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2))).list();
        return out;
    }
    public void copyGrants(String fromRole, String toRole) {
        db.sql("insert into app_role_permission(role_code, permission_code) select ?, permission_code from app_role_permission where role_code = ?")
                .params(toRole, fromRole).update();
    }
    public void replaceGrants(String roleCode, Set<String> permissions) {
        db.sql("delete from app_role_permission where role_code = ?").param(roleCode).update();
        for (String p : permissions) db.sql("insert into app_role_permission(role_code, permission_code) values (?, ?)").params(roleCode, p).update();
    }

    // ---------------------------------------------------------------- permissions
    public List<PermissionRecord> permissions() {
        return db.sql("select code, name, description, category, system from app_permission order by category, name")
                .query((rs, i) -> new PermissionRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5))).list();
    }
    /** permission code -> name */
    public Map<String, String> permissionNames() {
        return db.sql("select code, name from app_permission").query((rs, i) -> Map.entry(rs.getString(1), rs.getString(2))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    public void insertPermission(String code, String name, String description, String category, String createdBy) {
        db.sql("insert into app_permission(code, name, description, category, system, created_by) values (?, ?, ?, ?, false, ?)")
                .params(code, name, description, category, createdBy).update();
    }
    public void updatePermission(String code, String name, String description, String category) {
        db.sql("update app_permission set name = ?, description = ?, category = ? where code = ?").params(name, description, category, code).update();
    }
    /** Deletes a permission; its grants go with it (on delete cascade). */
    public void deletePermission(String code) { db.sql("delete from app_permission where code = ?").param(code).update(); }

    // ---------------------------------------------------------------- people affected by a change
    public List<String> emailsWithRole(String roleCode) { return db.sql("select email from app_user where role_code = ?").param(roleCode).query(String.class).list(); }
    public List<String> emailsWithPermission(String permissionCode) {
        return db.sql("select distinct u.email from app_user u join app_role_permission rp on rp.role_code = u.role_code where rp.permission_code = ?")
                .param(permissionCode).query(String.class).list();
    }

    // ---------------------------------------------------------------- change log
    public void log(String actor, String action, String subject, String details) {
        db.sql("insert into access_change_log(actor, action, subject, details) values (?, ?, ?, ?)").params(actor, action, subject, details).update();
    }
    public List<ChangeView> recentChanges(int limit) {
        return db.sql("select at, actor, action, subject, details from access_change_log order by at desc, id desc limit ?").param(limit)
                .query((rs, i) -> new ChangeView(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))).list();
    }
}
