package org.humsaadhak.identity.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import org.humsaadhak.identity.dto.Preferences;
import org.humsaadhak.identity.service.PreferencesService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

/** Personal settings of the signed-in person. */
@RestController
@RequestMapping("/api/v2/me/preferences")
public class PreferencesController {
    private final PreferencesService preferences;

    public PreferencesController(PreferencesService preferences) { this.preferences = preferences; }

    @GetMapping
    public Preferences get() { return preferences.get(CurrentUser.get().id()); }

    @AuditAction(value = "preferences.update", target = AuditTarget.USER, detail = "'Theme ' + #body.theme()")
    @PutMapping
    public Preferences save(@Valid @RequestBody Preferences body) { return preferences.save(CurrentUser.get().id(), body); }
}
