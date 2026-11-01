package org.humsaadhak.identity.service;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditContext;

import java.util.List;
import org.humsaadhak.identity.api.UserActivated;
import org.humsaadhak.identity.dto.UserRow;
import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.model.UserStatus;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.identity.util.RolePolicy;
import org.humsaadhak.registry.api.Organisations;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super admins: search users, change their role, disable or enable accounts, resend invitations. */
@Service
@Transactional
public class UserAdminService {
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final AccessService access;
    private final SessionRevoker sessions;
    private final Organisations organisations;

    public UserAdminService(AppUserRepository users, ApplicationEventPublisher events, AccessService access, SessionRevoker sessions,
                            Organisations organisations) {
        this.users = users; this.events = events; this.access = access; this.sessions = sessions; this.organisations = organisations;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserRow> list(String q, UserStatus status, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("fullName"));
        var result = q != null && !q.isBlank() ? users.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(q, q, pageable)
                : status != null ? users.findByStatus(status, pageable) : users.findAll(pageable);
        return PageResponse.of(result, this::row);
    }

    /** Gives a person any role (built-in or custom) that fits their account; they are signed out so it applies at once. */
    public UserRow changeRole(long id, String roleCode, CurrentUser me) {
        AppUser u = find(id);
        if (u.getId() == me.id()) throw new DomainException("own-role", "You cannot change your own role.");
        Role kind = access.kindOf(roleCode).orElseThrow(() -> new NotFoundException("Role", roleCode));
        String orgType = u.getOrgId() == null ? null : organisations.summary(u.getOrgId()).map(o -> o.type().name()).orElse(null);
        if (!RolePolicy.fits(kind, orgType))
            throw new DomainException("role-does-not-fit", orgType == null
                    ? "This person is HumSaadhak staff, so they need a staff role."
                    : "This person belongs to an organisation of a different type; choose a role for that type of account.");
        String before = access.roleName(u.getRoleCode());
        u.assignRole(roleCode, kind);
        access.log(me, "USER_ROLE_CHANGED", u.getEmail(), before + " → " + access.roleName(roleCode));
        sessions.revoke(List.of(u.getEmail()));
        return row(u);
    }

    /**
     * Adds a HumSaadhak staff member (manager, associate, super admin or a custom staff role). Staff do not register
     * themselves: the account is created active without a password and the person is emailed a single-use set-password link.
     */
    public UserRow createStaff(String email, String fullName, String mobile, String roleCode, CurrentUser me) {
        String address = email.strip();
        if (users.existsByEmailIgnoreCase(address))
            throw new DomainException("email-taken", "Someone already has an account with this email.");
        Role kind = access.kindOf(roleCode).orElseThrow(() -> new NotFoundException("Role", roleCode));
        if (!RolePolicy.fits(kind, null))
            throw new DomainException("role-does-not-fit", "Choose a staff role: this person is HumSaadhak staff.");
        AppUser u = new AppUser(address, fullName.strip(), mobile == null || mobile.isBlank() ? null : mobile.strip(), kind, null);
        u.assignRole(roleCode, kind);
        u.activate();
        u = users.save(u);
        AuditContext.target(AuditTarget.USER, u.getId());
        access.log(me, "USER_CREATED", u.getEmail(), access.roleName(roleCode));
        events.publishEvent(new UserActivated(u.getId(), u.getEmail(), u.getFullName()));   // emails the invitation link
        return row(u);
    }

    public UserRow disable(long id, CurrentUser me) {
        AppUser u = find(id);
        if (u.getId() == me.id()) throw new DomainException("own-account", "You cannot disable your own account.");
        u.disable();
        sessions.revoke(List.of(u.getEmail()));
        return row(u);
    }

    public UserRow enable(long id) {
        AppUser u = find(id);
        u.activate();
        return row(u);
    }

    /** Sends a fresh single-use set-password link (for example when the first one expired). */
    public void resendInvitation(long id) {
        AppUser u = find(id);
        if (u.getStatus() != UserStatus.ACTIVE) throw new DomainException("not-active", "Approve or enable the account before inviting.");
        events.publishEvent(new UserActivated(u.getId(), u.getEmail(), u.getFullName()));
    }

    private AppUser find(long id) { return users.findById(id).orElseThrow(() -> new NotFoundException("User", id)); }

    private UserRow row(AppUser u) {
        return new UserRow(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getRoleCode(), access.roleName(u.getRoleCode()), u.getStatus(), u.getOrgId(),
                u.getPasswordHash() != null, u.getCreatedAt());
    }
}
