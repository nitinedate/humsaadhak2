package org.humsaadhak.identity.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import org.humsaadhak.identity.dto.ChangeView;
import org.humsaadhak.identity.dto.KindOption;
import org.humsaadhak.identity.dto.PermissionBody;
import org.humsaadhak.identity.dto.PermissionView;
import org.humsaadhak.identity.dto.RoleBody;
import org.humsaadhak.identity.dto.RoleUpdate;
import org.humsaadhak.identity.dto.RoleView;

import org.humsaadhak.identity.service.AccessService;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Roles and permissions screen (super admins). The role list is also read by the Users screen. */
@RestController
@RequestMapping("/api/v2/admin/access")
@PreAuthorize("hasAuthority('perm:admin.roles')")
public class AccessController {
    private final AccessService access;
    AccessController(AccessService access) { this.access = access; }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('perm:admin.roles','perm:admin.users')")
    List<RoleView> roles() { return access.roles(); }

    @AuditAction(value = "role.create", target = AuditTarget.ROLE, id = "#result.code()", detail = "#b.name()")
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleView createRole(@Valid @RequestBody RoleBody b) {
        return access.createRole(b.name(), b.description(), b.kind(), b.copyFrom(), CurrentUser.get());
    }

    @AuditAction(value = "role.update", target = AuditTarget.ROLE, id = "#code")
    @PutMapping("/roles/{code}")
    RoleView updateRole(@PathVariable String code, @Valid @RequestBody RoleUpdate b) {
        return access.updateRole(code, b.name(), b.description(), b.permissions(), CurrentUser.get());
    }

    @AuditAction(value = "role.delete", target = AuditTarget.ROLE, id = "#code")
    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRole(@PathVariable String code) { access.deleteRole(code, CurrentUser.get()); }

    @GetMapping("/permissions")
    List<PermissionView> permissions() { return access.permissions(); }

    @AuditAction(value = "permission.create", target = AuditTarget.PERMISSION, id = "#result.code()")
    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    PermissionView createPermission(@Valid @RequestBody PermissionBody b) {
        return access.createPermission(b.code(), b.name(), b.description(), b.category(), CurrentUser.get());
    }

    @AuditAction(value = "permission.update", target = AuditTarget.PERMISSION, id = "#code")
    @PutMapping("/permissions/{code}")
    PermissionView updatePermission(@PathVariable String code, @Valid @RequestBody PermissionBody b) {
        return access.updatePermission(code, b.name(), b.description(), b.category(), CurrentUser.get());
    }

    @AuditAction(value = "permission.delete", target = AuditTarget.PERMISSION, id = "#code")
    @DeleteMapping("/permissions/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePermission(@PathVariable String code) { access.deletePermission(code, CurrentUser.get()); }

    @GetMapping("/kinds")
    List<KindOption> kinds() { return AccessService.kinds(); }

    @GetMapping("/changes")
    List<ChangeView> changes() { return access.changes(); }
}
