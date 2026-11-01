package org.humsaadhak.registry.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.registry.api.OrganisationType;
import org.humsaadhak.registry.api.Organisations;
import org.humsaadhak.registry.dto.BankBody;
import org.humsaadhak.registry.dto.BankRow;
import org.humsaadhak.registry.dto.ContactBody;
import org.humsaadhak.registry.dto.ContactRow;
import org.humsaadhak.registry.dto.ProfileBody;
import org.humsaadhak.registry.service.OrganisationProfileService;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Organisations: staff list; profile, contacts and bank accounts of one organisation. */
@RestController
@RequestMapping("/api/v2/organisations")
public class OrganisationController {
    private final OrganisationProfileService profiles;

    public OrganisationController(OrganisationProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    @PreAuthorize("hasAuthority('perm:dashboard.platform')")
    public PageResponse<Organisations.Summary> list(@RequestParam(required = false) OrganisationType type, @RequestParam(required = false) Boolean verified,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return profiles.list(type, verified, q, page, size);
    }

    @GetMapping("/me")
    public Organisations.Summary mine() { return profiles.mine(CurrentUser.get()); }

    @GetMapping("/{id}")
    public Organisations.Summary get(@PathVariable long id) { return profiles.get(id, CurrentUser.get()); }

    @AuditAction(value = "organisation.profile-update", target = AuditTarget.ORGANISATION, id = "#id")
    @PutMapping("/{id}/profile")
    public Organisations.Summary updateProfile(@PathVariable long id, @Valid @RequestBody ProfileBody body) {
        return profiles.updateProfile(id, body, CurrentUser.get());
    }

    @GetMapping("/{id}/contacts")
    public List<ContactRow> contacts(@PathVariable long id) { return profiles.contacts(id, CurrentUser.get()); }

    /** Replaces the whole contact list in one go (the form sends every row). */
    @AuditAction(value = "organisation.contacts-update", target = AuditTarget.ORGANISATION, id = "#id", detail = "#body.size() + ' contacts'")
    @PutMapping("/{id}/contacts")
    public List<ContactRow> replaceContacts(@PathVariable long id, @Valid @RequestBody List<ContactBody> body) {
        return profiles.replaceContacts(id, body, CurrentUser.get());
    }

    @GetMapping("/{id}/bank-accounts")
    public List<BankRow> bankAccounts(@PathVariable long id) { return profiles.bankAccounts(id, CurrentUser.get()); }

    @AuditAction(value = "organisation.bank-add", target = AuditTarget.ORGANISATION, id = "#id", detail = "'Account ' + #result.accountNumber()")
    @PostMapping("/{id}/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankRow addBankAccount(@PathVariable long id, @Valid @RequestBody BankBody b) { return profiles.addBankAccount(id, b, CurrentUser.get()); }
}
