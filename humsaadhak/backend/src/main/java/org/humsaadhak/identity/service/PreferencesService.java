package org.humsaadhak.identity.service;

import org.humsaadhak.identity.dto.Preferences;
import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.repository.AppUserRepository;
import org.humsaadhak.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal settings saved on the account (today: the colour theme), so they are the same on every device. */
@Service
@Transactional
public class PreferencesService {
    private final AppUserRepository users;

    public PreferencesService(AppUserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public Preferences get(long userId) { return new Preferences(user(userId).getTheme()); }

    public Preferences save(long userId, Preferences wanted) {
        AppUser u = user(userId);
        u.setTheme(wanted.theme());
        return new Preferences(u.getTheme());
    }

    private AppUser user(long id) { return users.findById(id).orElseThrow(() -> new NotFoundException("User", id)); }
}
