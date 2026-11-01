package org.humsaadhak.registry.service;

import java.util.List;
import org.humsaadhak.registry.api.OrganisationType;
import org.humsaadhak.registry.api.Organisations;
import org.humsaadhak.registry.dto.BankBody;
import org.humsaadhak.registry.dto.BankRow;
import org.humsaadhak.registry.dto.ContactBody;
import org.humsaadhak.registry.dto.ContactRow;
import org.humsaadhak.registry.dto.ProfileBody;
import org.humsaadhak.registry.entity.BankAccount;
import org.humsaadhak.registry.entity.OrgContact;
import org.humsaadhak.registry.repository.BankAccountRepository;
import org.humsaadhak.registry.repository.OrgContactRepository;
import org.humsaadhak.registry.repository.OrganisationRepository;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An organisation's profile, contacts and bank accounts. People see and change only their own organisation; staff
 * can see and change any. Bank account numbers are stored encrypted and only ever shown masked.
 */
@Service
@Transactional
public class OrganisationProfileService {
    private final OrganisationRepository orgs;
    private final OrgContactRepository contacts;
    private final BankAccountRepository accounts;
    private final OrganisationService organisations;

    public OrganisationProfileService(OrganisationRepository orgs, OrgContactRepository contacts, BankAccountRepository accounts,
                                      OrganisationService organisations) {
        this.orgs = orgs; this.contacts = contacts; this.accounts = accounts; this.organisations = organisations;
    }

    @Transactional(readOnly = true)
    public PageResponse<Organisations.Summary> list(OrganisationType type, Boolean verified, String q, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name"));
        var result = q != null && !q.isBlank() ? orgs.findByNameContainingIgnoreCase(q, pageable)
                : type != null && verified != null ? orgs.findByTypeAndVerified(type, verified, pageable)
                : type != null ? orgs.findByType(type, pageable) : orgs.findAll(pageable);
        return PageResponse.of(result, o -> organisations.summary(o.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Organisations.Summary mine(CurrentUser me) {
        if (me.orgId() == null) throw new NotFoundException("Organisation for user", me.id());
        return get(me.orgId(), me);
    }

    @Transactional(readOnly = true)
    public Organisations.Summary get(long id, CurrentUser me) {
        checkAccess(id, me);
        return organisations.summary(id).orElseThrow(() -> new NotFoundException("Organisation", id));
    }

    public Organisations.Summary updateProfile(long id, ProfileBody body, CurrentUser me) {
        checkAccess(id, me);
        orgs.findById(id).orElseThrow(() -> new NotFoundException("Organisation", id)).updateProfile(body.name(), body.city(), body.state());
        return organisations.summary(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ContactRow> contacts(long id, CurrentUser me) {
        checkAccess(id, me);
        return contacts.findByOrgIdOrderByIdAsc(id).stream()
                .map(c -> new ContactRow(c.getRole(), c.getName(), c.getDesignation(), c.getEmail(), c.getPhone())).toList();
    }

    /** Replaces the whole contact list in one go (the form sends every row). */
    public List<ContactRow> replaceContacts(long id, List<ContactBody> body, CurrentUser me) {
        checkAccess(id, me);
        contacts.deleteByOrgId(id);
        body.forEach(c -> contacts.save(new OrgContact(id, c.role(), c.name(), c.designation(), c.email(), c.phone())));
        return contacts(id, me);
    }

    @Transactional(readOnly = true)
    public List<BankRow> bankAccounts(long id, CurrentUser me) {
        checkAccess(id, me);
        return accounts.findByOrgIdOrderByIdAsc(id).stream()
                .map(b -> new BankRow(b.getId(), b.getAccountName(), b.maskedNumber(), b.getIfsc(), b.getBankName())).toList();
    }

    public BankRow addBankAccount(long id, BankBody b, CurrentUser me) {
        checkAccess(id, me);
        BankAccount saved = accounts.save(new BankAccount(id, b.accountName(), b.accountNumber(), b.ifsc(), b.bankName(), b.branch()));
        return new BankRow(saved.getId(), saved.getAccountName(), saved.maskedNumber(), saved.getIfsc(), saved.getBankName());
    }

    private static void checkAccess(long orgId, CurrentUser me) {
        if (!me.role().isStaff() && (me.orgId() == null || me.orgId() != orgId)) throw new AccessDeniedException("organisation " + orgId);
    }
}
