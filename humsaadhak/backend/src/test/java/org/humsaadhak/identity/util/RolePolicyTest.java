package org.humsaadhak.identity.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.security.Role;
import org.junit.jupiter.api.Test;

class RolePolicyTest {
    @Test
    void newRolesGetACodeFromTheirName() {
        assertThat(RolePolicy.newRoleCode("Regional coordinator", Set.of("MANAGER"))).isEqualTo("REGIONAL_COORDINATOR");
        assertThatThrownBy(() -> RolePolicy.newRoleCode("Manager", Set.of("MANAGER"))).hasMessageContaining("already exists");
    }

    @Test
    void customRolesTakeAnAccountTypeButNeverSuperAdminOrGuest() {
        assertThat(RolePolicy.customKind("ASSOCIATE")).isEqualTo(Role.ASSOCIATE);
        assertThat(RolePolicy.customKind("NGO")).isEqualTo(Role.NGO);
        assertThatThrownBy(() -> RolePolicy.customKind("SUPER_ADMIN")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RolePolicy.customKind("GUEST")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RolePolicy.customKind("anything")).hasMessageContaining("type of account");
    }

    @Test
    void permissionCodesAreLowerCaseDottedAndUnique() {
        assertThat(RolePolicy.newPermissionCode(" Reports.View ", Set.of())).isEqualTo("reports.view");
        assertThat(RolePolicy.newPermissionCode("field-visits.approve", Set.of())).isEqualTo("field-visits.approve");
        assertThatThrownBy(() -> RolePolicy.newPermissionCode("reports", Set.of())).hasMessageContaining("reports.view");
        assertThatThrownBy(() -> RolePolicy.newPermissionCode("reports.view; drop table", Set.of())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RolePolicy.newPermissionCode("admin.users", Set.of("admin.users"))).hasMessageContaining("already exists");
    }

    @Test
    void nobodyCanLockThePlatformOut() {
        Set<String> known = Set.of("admin.users", "admin.roles", "import.run");
        RolePolicy.checkPermissions("SUPER_ADMIN", Set.of("admin.users", "admin.roles"), known);
        assertThatThrownBy(() -> RolePolicy.checkPermissions("SUPER_ADMIN", Set.of("admin.users"), known)).hasMessageContaining("always keeps");
        RolePolicy.checkPermissions("MANAGER", Set.of(), known);                       // other roles may have none
        assertThatThrownBy(() -> RolePolicy.checkPermissions("MANAGER", Set.of("made.up"), known)).hasMessageContaining("Unknown permission made.up");
    }

    @Test
    void builtInRolesAndPermissionsStayAndRolesInUseAreKept() {
        assertThatThrownBy(() -> RolePolicy.checkDeleteRole(true, 0)).hasMessageContaining("cannot be deleted");
        assertThatThrownBy(() -> RolePolicy.checkDeleteRole(false, 3)).hasMessageContaining("3 people have this role");
        assertThatThrownBy(() -> RolePolicy.checkDeleteRole(false, 1)).hasMessageContaining("1 person has this role");
        RolePolicy.checkDeleteRole(false, 0);
        assertThatThrownBy(() -> RolePolicy.checkRename(true)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RolePolicy.checkEditPermission(true)).hasMessageContaining("Built-in permissions");
    }

    @Test
    void rolesMustFitThePersonsAccount() {
        assertThat(RolePolicy.fits(Role.ASSOCIATE, null)).isTrue();
        assertThat(RolePolicy.fits(Role.ASSOCIATE, "NGO")).isFalse();
        assertThat(RolePolicy.fits(Role.NGO, "NGO")).isTrue();
        assertThat(RolePolicy.fits(Role.DONOR_CORPORATE, "DONOR_INDIVIDUAL")).isFalse();
        assertThat(RolePolicy.fits(Role.SCHOOL, null)).isFalse();
    }

    @Test
    void changesAreDescribedInPlainWords() {
        Map<String, String> names = Map.of("import.run", "Bulk import", "record.approve", "Approve records");
        assertThat(RolePolicy.describeChange(Set.of("import.run"), Set.of("record.approve"), names)).isEqualTo("Added: Approve records; Removed: Bulk import");
        assertThat(RolePolicy.describeChange(Set.of("a.b"), Set.of("a.b"), names)).isEqualTo("No permission changes");
    }
}
