package org.humsaadhak.identity.service;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditContext;

import org.humsaadhak.identity.dto.Registration;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.identity.entity.OneTimeToken;
import org.humsaadhak.identity.repository.OneTimeTokenRepository;
import org.humsaadhak.identity.util.PasswordPolicy;
import org.humsaadhak.identity.util.TokenHasher;
import org.humsaadhak.identity.model.UserStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.humsaadhak.identity.api.InvitationLinks;
import org.humsaadhak.identity.api.PasswordResetRequested;
import org.humsaadhak.shared.security.Role;
import org.humsaadhak.identity.api.UserActivated;
import org.humsaadhak.identity.api.UserRegistered;
import org.humsaadhak.registry.api.OrganisationType;
import org.humsaadhak.registry.api.Organisations;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.workflow.api.ApprovalDecided;
import org.humsaadhak.workflow.api.Approvals;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up, approval follow-up and passwords.
 * Fixes S3 (no temporary password, nothing secret logged) and S5 (tokens expire and are single-use).
 */
@Service
public class AccountService implements InvitationLinks {
    public static final Duration INVITATION_TTL = Duration.ofHours(72);
    public static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final AppUserRepository users;
    private final OneTimeTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Organisations organisations;
    private final Approvals approvals;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String appUrl;

    public AccountService(AppUserRepository users, OneTimeTokenRepository tokens, PasswordEncoder encoder,
                   Organisations organisations, Approvals approvals, ApplicationEventPublisher events,
                   Clock clock, @Value("${humsaadhak.app-url}") String appUrl) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.organisations = organisations;
        this.approvals = approvals; this.events = events; this.clock = clock; this.appUrl = appUrl;
    }

    @Transactional
    public long register(Registration r) {
        if (!r.consent()) throw new DomainException("consent-required", "Please accept the data-use consent to continue.");
        if (r.role() == null || r.role().isStaff() || r.role() == Role.GUEST)
            throw new DomainException("role-not-allowed", "Choose donor, school or NGO.");
        if (users.existsByEmailIgnoreCase(r.email()))
            throw new DomainException("email-taken", "An account with this email already exists. Sign in or reset your password.");

        String orgName = r.role() == Role.DONOR_INDIVIDUAL ? r.fullName() : r.organisationName();
        Organisations.Joined org = organisations.findOrCreateForRegistration(orgName, organisationTypeFor(r.role()), r.city(), r.state());

        AppUser user = users.save(new AppUser(r.email(), r.fullName(), r.mobile(), r.role(), org.orgId()));
        AuditContext.target(AuditTarget.USER, user.getId());
        approvals.openAndSubmit(SubjectType.USER, user.getId(), user.getId(), user.getFullName() + " (" + user.getRole() + ")");
        events.publishEvent(new UserRegistered(user.getId(), org.orgId(), org.existing()));
        return user.getId();
    }

    /** A manager decided on a USER approval request. */
    @ApplicationModuleListener
    public void on(ApprovalDecided e) {
        if (e.subjectType() != SubjectType.USER) return;
        AppUser user = users.findById(e.subjectId()).orElseThrow(() -> new NotFoundException("User", e.subjectId()));
        if (e.approved()) {
            user.activate();
            events.publishEvent(new UserActivated(user.getId(), user.getEmail(), user.getFullName()));
        } else {
            user.reject();
        }
    }

    public static OrganisationType organisationTypeFor(Role role) {
        return switch (role) {
            case DONOR_INDIVIDUAL -> OrganisationType.DONOR_INDIVIDUAL;
            case DONOR_CORPORATE -> OrganisationType.DONOR_CORPORATE;
            case SCHOOL -> OrganisationType.SCHOOL;
            case NGO -> OrganisationType.NGO;
            default -> throw new DomainException("role-not-allowed", "Choose donor, school or NGO.");
        };
    }

    @Override @Transactional
    public String issueInvitationLink(long userId) {
        return appUrl + "/set-password?token=" + issue(userId, OneTimeToken.Purpose.INVITATION, INVITATION_TTL);
    }

    @Override @Transactional
    public String issuePasswordResetLink(long userId) {
        return appUrl + "/set-password?token=" + issue(userId, OneTimeToken.Purpose.PASSWORD_RESET, RESET_TTL);
    }

    private String issue(long userId, OneTimeToken.Purpose purpose, Duration ttl) {
        String raw = TokenHasher.newRawToken();
        tokens.save(new OneTimeToken(userId, purpose, TokenHasher.hash(raw), Instant.now(clock).plus(ttl)));
        return raw;
    }

    /** Works for both the invitation and the reset link. */
    @Transactional
    public void setPassword(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        OneTimeToken t = tokens.findByTokenHash(TokenHasher.hash(rawToken))
                .filter(tok -> tok.usable(now))
                .orElseThrow(() -> new DomainException("link-expired", "This link has expired or was already used. Ask for a new one."));
        AppUser user = users.findById(t.getUserId()).orElseThrow(() -> new NotFoundException("User", t.getUserId()));
        AuditContext.target(AuditTarget.USER, user.getId());
        List<String> problems = PasswordPolicy.problems(newPassword, user.getEmail());
        if (!problems.isEmpty()) throw new DomainException("weak-password", String.join(" ", problems));
        user.setPasswordHash(encoder.encode(newPassword));
        t.markUsed(now);
    }

    /** Always succeeds from the caller's view so the endpoint cannot be used to discover accounts. */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .ifPresent(u -> {
                    AuditContext.target(AuditTarget.USER, u.getId());
                    events.publishEvent(new PasswordResetRequested(u.getId(), u.getEmail(), u.getFullName()));
                });
    }

}
