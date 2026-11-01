package org.humsaadhak.identity.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import org.humsaadhak.identity.service.AuthService;
import org.humsaadhak.identity.dto.EmailBody;
import org.humsaadhak.identity.dto.LoginRequest;
import org.humsaadhak.identity.dto.MeResponse;
import org.humsaadhak.identity.dto.Registration;
import org.humsaadhak.identity.dto.RegistrationRequest;
import org.humsaadhak.identity.dto.SetPasswordBody;

import org.humsaadhak.identity.service.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class AuthController {
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final AccountService accounts;
    private final AuthService auth;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepository, AccountService accounts, AuthService auth) {
        this.authManager = authManager; this.contextRepository = contextRepository; this.accounts = accounts; this.auth = auth;
    }

    /** Signs in and starts a new session (the session id changes, against session fixation). */
    @AuditAction(value = "auth.sign-in", target = AuditTarget.USER, detail = "#body.email()", transactional = false)
    @PostMapping("/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        auth.assertNotLocked(body.email());
        try {
            Authentication authentication = authManager.authenticate(new UsernamePasswordAuthenticationToken(body.email(), body.password()));
            auth.loginSucceeded(body.email());
            // A new session id, against session fixation: an existing session (an earlier sign-in in this browser) gets a
            // new id; without one, a new session is created, which has a fresh id anyway. changeSessionId() alone throws
            // when there is no session, which is every first sign-in: the CSRF token lives in a cookie, not the session.
            if (req.getSession(false) != null) req.changeSessionId();
            else req.getSession(true);
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(authentication);
            SecurityContextHolder.setContext(ctx);
            contextRepository.saveContext(ctx, req, res);
            return me();
        } catch (AuthenticationException e) {
            auth.loginFailed(body.email());
            throw new DomainException("bad-credentials", "Email or password is not correct, or the account is not active yet.");
        }
    }

    @AuditAction(value = "auth.sign-out", target = AuditTarget.USER, transactional = false)
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req) {
        var session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public MeResponse me() {
        CurrentUser u = CurrentUser.get();
        return new MeResponse(u.id(), u.email(), u.fullName(), u.orgId(), u.role(), u.roleCode(), u.roleName(), u.permissions(), auth.themeOf(u.id()));
    }

    @AuditAction(value = "auth.register", target = AuditTarget.USER, detail = "#r.email()")
    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegistrationRequest r) {
        accounts.register(new Registration(r.email(), r.fullName(), r.mobile(), r.role(),
                r.organisationName(), r.city(), r.state(), r.consent()));
    }

    @AuditAction(value = "auth.reset-request", target = AuditTarget.USER, detail = "#body.email()")
    @PostMapping("/auth/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody EmailBody body) { accounts.requestPasswordReset(body.email()); }

    @AuditAction(value = "auth.set-password", target = AuditTarget.USER)
    @PostMapping("/auth/set-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@Valid @RequestBody SetPasswordBody body) { accounts.setPassword(body.token(), body.password()); }
}
