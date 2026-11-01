package org.humsaadhak.reporting.service;

import java.util.List;
import org.humsaadhak.reporting.dao.LookupDao;
import org.humsaadhak.reporting.dto.OpenNeed;
import org.humsaadhak.reporting.dto.OrganisationOption;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pickers in forms: open causes and projects (donors and staff) and organisations by group (staff only). */
@Service
@Transactional(readOnly = true)
public class LookupService {
    private final LookupDao lookups;

    public LookupService(LookupDao lookups) { this.lookups = lookups; }

    public List<OpenNeed> openNeeds(CurrentUser me, String q) {
        if (!me.role().isStaff() && !me.role().isDonor()) throw new AccessDeniedException("donors and staff only");
        return lookups.openNeeds(like(q));
    }

    public List<OrganisationOption> organisations(CurrentUser me, String group, String q) {
        if (!me.role().isStaff()) throw new AccessDeniedException("staff only");
        LookupDao.Group g;
        try { g = LookupDao.Group.valueOf(group); } catch (RuntimeException e) { throw new DomainException("bad-group", "Use DONORS or BENEFICIARIES."); }
        return lookups.organisations(g, like(q));
    }

    private static String like(String q) { return q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase() + "%"; }
}
