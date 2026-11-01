package org.humsaadhak.identity.service;

import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.identity.model.UserStatus;

import java.util.List;
import org.humsaadhak.identity.api.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AppUserDirectory implements UserDirectory {
    private final AppUserRepository users;
    public AppUserDirectory(AppUserRepository users) { this.users = users; }

    @Override @Transactional(readOnly = true)
    public List<Recipient> activeUsersOfOrg(long orgId) {
        return users.findByOrgId(orgId).stream().filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(u -> new Recipient(u.getEmail(), u.getFullName())).toList();
    }
}
