package org.humsaadhak.identity.config;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;

import org.humsaadhak.identity.api.UserActivated;
import org.humsaadhak.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first super admin on a fresh installation without any default password.
 * Set HUMSAADHAK_BOOTSTRAP_ADMIN_EMAIL; on first start the account is created and an invitation
 * (single-use, 72-hour set-password link) is emailed. Does nothing once any super admin exists.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final String email;
    private final String name;

    BootstrapAdmin(AppUserRepository users, ApplicationEventPublisher events,
                   @Value("${humsaadhak.bootstrap.admin-email:}") String email,
                   @Value("${humsaadhak.bootstrap.admin-name:Platform administrator}") String name) {
        this.users = users; this.events = events; this.email = email; this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || users.existsByRole(Role.SUPER_ADMIN)) return;
        AppUser admin = new AppUser(email, name, null, Role.SUPER_ADMIN, null);
        admin.activate();
        users.save(admin);
        events.publishEvent(new UserActivated(admin.getId(), admin.getEmail(), admin.getFullName()));
        log.info("Created the first super admin {}; an invitation email has been queued.", admin.getEmail());
    }
}
