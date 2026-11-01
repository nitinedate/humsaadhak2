package org.humsaadhak.identity.service;

import java.time.Clock;
import java.time.Instant;
import org.humsaadhak.identity.api.Theme;
import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.shared.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in rules: lock an account after repeated failures (AppUser.MAX_FAILED_LOGINS) and reset the count on success. */
@Service
@Transactional
public class AuthService {
    private final AppUserRepository users;
    private final Clock clock;

    public AuthService(AppUserRepository users, Clock clock) { this.users = users; this.clock = clock; }

    @Transactional(readOnly = true)
    public void assertNotLocked(String email) {
        users.findByEmailIgnoreCase(email).filter(u -> u.isLocked(Instant.now(clock))).ifPresent(u -> {
            throw new DomainException("locked", "Too many failed attempts. Try again in " + AppUser.LOCK_MINUTES + " minutes.");
        });
    }

    public void loginSucceeded(String email) { users.findByEmailIgnoreCase(email).ifPresent(AppUser::recordSuccessfulLogin); }

    public void loginFailed(String email) { users.findByEmailIgnoreCase(email).ifPresent(u -> u.recordFailedLogin(Instant.now(clock))); }

    @Transactional(readOnly = true)
    public Theme themeOf(long userId) { return users.findById(userId).map(AppUser::getTheme).orElse(Theme.INDIGO); }
}
