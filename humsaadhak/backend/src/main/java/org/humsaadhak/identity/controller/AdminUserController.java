package org.humsaadhak.identity.controller;

import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.HttpStatus;

import org.humsaadhak.identity.dto.NewStaffBody;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import org.humsaadhak.identity.dto.RoleChange;
import org.humsaadhak.identity.dto.UserRow;
import org.humsaadhak.identity.model.UserStatus;
import org.humsaadhak.identity.service.UserAdminService;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Users screen (super admins). */
@RestController
@RequestMapping("/api/v2/admin/users")
@PreAuthorize("hasAuthority('perm:admin.users')")
public class AdminUserController {
    private final UserAdminService users;

    public AdminUserController(UserAdminService users) { this.users = users; }

    @GetMapping
    public PageResponse<UserRow> list(@RequestParam(required = false) String q, @RequestParam(required = false) UserStatus status,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return users.list(q, status, page, size);
    }

    @AuditAction(value = "user.create", target = AuditTarget.USER, id = "#result.id()", detail = "#b.email() + ' as ' + #b.roleCode()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRow create(@Valid @RequestBody NewStaffBody b) {
        return users.createStaff(b.email(), b.fullName(), b.mobile(), b.roleCode(), CurrentUser.get());
    }

    @AuditAction(value = "user.role-change", target = AuditTarget.USER, id = "#id")
    @PutMapping("/{id}/role")
    public UserRow changeRole(@PathVariable long id, @Valid @RequestBody RoleChange body) { return users.changeRole(id, body.roleCode(), CurrentUser.get()); }

    @AuditAction(value = "user.disable", target = AuditTarget.USER, id = "#id")
    @PostMapping("/{id}/disable")
    public UserRow disable(@PathVariable long id) { return users.disable(id, CurrentUser.get()); }

    @AuditAction(value = "user.enable", target = AuditTarget.USER, id = "#id")
    @PostMapping("/{id}/enable")
    public UserRow enable(@PathVariable long id) { return users.enable(id); }

    @AuditAction(value = "user.invite", target = AuditTarget.USER, id = "#id")
    @PostMapping("/{id}/invite")
    public void resendInvitation(@PathVariable long id) { users.resendInvitation(id); }
}
